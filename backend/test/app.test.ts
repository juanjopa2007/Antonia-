import type { FastifyInstance } from 'fastify';
import { afterEach, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { createServerTools } from '../src/tools/index.js';
import { TOKEN, scriptedAnthropic, testConfig, type ScriptedTurn } from './helpers.js';

const validBody = {
  messages: [{ role: 'user', content: '¿Cuánto es el 15% de 80?' }],
  clientTools: [],
  context: { now: '2026-11-19T09:30:00+01:00', timezone: 'Europe/Madrid' },
};

const auth = { authorization: `Bearer ${TOKEN}` };

let app: FastifyInstance | undefined;
afterEach(async () => {
  await app?.close();
  app = undefined;
});

async function start(turns: ScriptedTurn[], configOverrides = {}, fetchImpl?: typeof fetch) {
  const { anthropic, calls } = scriptedAnthropic(turns);
  const config = testConfig(configOverrides);
  app = await buildApp({ config, anthropic, ...(fetchImpl ? { fetchImpl } : {}) });
  return { app, calls };
}

function parseSse(payload: string): { event: string; data: Record<string, unknown> }[] {
  return payload
    .split('\n\n')
    .filter((block) => block.startsWith('event:'))
    .map((block) => {
      const [eventLine, dataLine] = block.split('\n');
      return {
        event: eventLine!.slice('event: '.length),
        data: JSON.parse(dataLine!.slice('data: '.length)) as Record<string, unknown>,
      };
    });
}

describe('GET /healthz', () => {
  it('responde sin autenticación', async () => {
    const { app } = await start([]);
    const res = await app.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ ok: true });
  });
});

describe('POST /v1/chat', () => {
  it('rechaza peticiones sin token o con token incorrecto', async () => {
    const { app, calls } = await start([]);
    const none = await app.inject({ method: 'POST', url: '/v1/chat', payload: validBody });
    const bad = await app.inject({
      method: 'POST',
      url: '/v1/chat',
      headers: { authorization: 'Bearer incorrecto' },
      payload: validBody,
    });
    expect(none.statusCode).toBe(401);
    expect(bad.statusCode).toBe(401);
    expect(calls).toHaveLength(0);
  });

  it('valida el cuerpo', async () => {
    const { app } = await start([]);
    const cases = [
      {},
      { ...validBody, messages: [] },
      { ...validBody, messages: [{ role: 'assistant', content: 'hola' }] },
      { ...validBody, context: { now: 'ayer', timezone: 'Europe/Madrid' } },
      { ...validBody, clientTools: [{ name: 'mala herramienta', description: 'x', input_schema: { type: 'object' } }] },
    ];
    for (const payload of cases) {
      const res = await app.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload });
      expect(res.statusCode, JSON.stringify(payload)).toBe(400);
    }
  });

  it('rechaza herramientas de cliente que chocan con las del servidor', async () => {
    const { app } = await start([]);
    const res = await app.inject({
      method: 'POST',
      url: '/v1/chat',
      headers: auth,
      payload: {
        ...validBody,
        clientTools: [{ name: 'calculator', description: 'x', input_schema: { type: 'object' } }],
      },
    });
    expect(res.statusCode).toBe(400);
    expect(res.body).toContain('calculator');
  });

  it('devuelve un flujo SSE con la calculadora ejecutada en el servidor', async () => {
    const { app, calls } = await start([
      {
        content: [{ type: 'tool_use', id: 'tu_1', name: 'calculator', input: { expression: '0.15 * 80' } }],
        stopReason: 'tool_use',
      },
      { text: ['Son doce.'], content: [{ type: 'text', text: 'Son doce.' }], stopReason: 'end_turn' },
    ]);

    const res = await app.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload: validBody });
    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toContain('text/event-stream');

    const events = parseSse(res.body);
    expect(events.map((e) => e.event)).toEqual(['tool_status', 'tool_status', 'text', 'done']);
    expect(events[2]!.data).toMatchObject({ delta: 'Son doce.' });

    const secondMessages = calls[1]!['messages'] as { content: { content?: string }[] }[];
    expect(secondMessages.at(-1)!.content[0]!.content).toBe('12');
  });

  it('no expone la clave de Anthropic ni detalles internos si Claude falla', async () => {
    const { app } = await start([
      { content: [], stopReason: 'end_turn', error: Object.assign(new Error('401 sk-ant-secreto'), { status: 401 }) },
    ]);
    const res = await app.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload: validBody });
    expect(res.statusCode).toBe(200);
    expect(res.body).not.toContain('sk-ant-secreto');
    const events = parseSse(res.body);
    expect(events.at(-1)).toMatchObject({ event: 'error', data: { code: 'upstream_auth' } });
  });

  it('aplica el límite de peticiones por token', async () => {
    const turn: ScriptedTurn = { content: [{ type: 'text', text: 'ok' }], stopReason: 'end_turn' };
    const { app } = await start([turn, turn, turn], { rateLimitPerMinute: 2 });
    const codes: number[] = [];
    for (let i = 0; i < 3; i++) {
      const res = await app.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload: validBody });
      codes.push(res.statusCode);
    }
    expect(codes).toEqual([200, 200, 429]);
  });

  it('solo ofrece la búsqueda web si hay clave configurada', async () => {
    const { calls } = await start([{ content: [{ type: 'text', text: 'ok' }], stopReason: 'end_turn' }]);
    await app!.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload: validBody });
    expect((calls[0]!['tools'] as { name: string }[]).map((t) => t.name)).toEqual(['calculator']);

    await app!.close();
    const second = await start(
      [{ content: [{ type: 'text', text: 'ok' }], stopReason: 'end_turn' }],
      { braveApiKey: 'brave-key' },
    );
    await second.app.inject({ method: 'POST', url: '/v1/chat', headers: auth, payload: validBody });
    expect((second.calls[0]!['tools'] as { name: string }[]).map((t) => t.name)).toEqual(['calculator', 'web_search']);
  });
});

describe('herramienta web_search', () => {
  it('llama a Brave con la clave y formatea los resultados', async () => {
    let requested: { url: string; token: string | null } | undefined;
    const fakeFetch = (async (url: string | URL | Request, init?: RequestInit) => {
      requested = {
        url: String(url),
        token: new Headers(init?.headers).get('X-Subscription-Token'),
      };
      return new Response(
        JSON.stringify({
          web: { results: [{ title: 'Tiempo en <b>Madrid</b>', url: 'https://ejemplo.es', description: 'Soleado &amp; 18 grados' }] },
        }),
        { status: 200, headers: { 'content-type': 'application/json' } },
      );
    }) as typeof fetch;

    const registry = createServerTools(testConfig({ braveApiKey: 'brave-key' }), fakeFetch);
    const output = await registry.get('web_search')!.execute({ query: 'tiempo Madrid', country: 'es' });

    expect(requested?.token).toBe('brave-key');
    expect(requested?.url).toContain('q=tiempo+Madrid');
    expect(requested?.url).toContain('country=ES');
    expect(output).toContain('Tiempo en Madrid');
    expect(output).toContain('Soleado & 18 grados');
    expect(output).toContain('https://ejemplo.es');
  });

  it('informa de errores del servicio y de búsquedas sin resultados', async () => {
    const failing = (async () => new Response('x', { status: 500 })) as typeof fetch;
    const empty = (async () => new Response(JSON.stringify({ web: { results: [] } }), { status: 200 })) as typeof fetch;

    await expect(
      createServerTools(testConfig({ braveApiKey: 'k' }), failing).get('web_search')!.execute({ query: 'x' }),
    ).rejects.toThrow(/500/);
    await expect(
      createServerTools(testConfig({ braveApiKey: 'k' }), empty).get('web_search')!.execute({ query: 'x' }),
    ).resolves.toMatch(/Sin resultados/);
  });
});
