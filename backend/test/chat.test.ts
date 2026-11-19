import { describe, expect, it } from 'vitest';
import { describeError, runChat, type ChatEvent } from '../src/chat.js';
import { ToolRegistry } from '../src/tools/index.js';
import { scriptedAnthropic, testConfig } from './helpers.js';

const context = { now: '2026-11-19T09:30:00+01:00', timezone: 'Europe/Madrid' };

const openAppTool = {
  name: 'open_app',
  description: 'Abre una aplicación instalada',
  input_schema: { type: 'object' as const, properties: { name: { type: 'string' } }, required: ['name'] },
};

const echoTool = {
  definition: {
    name: 'echo',
    description: 'Devuelve el texto',
    input_schema: { type: 'object' as const, properties: { text: { type: 'string' } } },
  },
  async execute(input: unknown) {
    return `eco: ${(input as { text: string }).text}`;
  },
};

const failingTool = {
  definition: { name: 'boom', description: 'Siempre falla', input_schema: { type: 'object' as const } },
  async execute(): Promise<string> {
    throw new Error('fallo simulado');
  },
};

function collect() {
  const events: ChatEvent[] = [];
  return { events, emit: (e: ChatEvent) => events.push(e) };
}

describe('bucle de chat', () => {
  it('responde con texto en streaming y termina', async () => {
    const { anthropic, calls } = scriptedAnthropic([
      { text: ['Hola, ', 'buenos días.'], content: [{ type: 'text', text: 'Hola, buenos días.' }], stopReason: 'end_turn' },
    ]);
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([]), emit },
      { messages: [{ role: 'user', content: 'Hola' }], clientTools: [], context },
    );

    expect(events.filter((e) => e.type === 'text').map((e) => (e as { delta: string }).delta)).toEqual([
      'Hola, ',
      'buenos días.',
    ]);
    const done = events.at(-1);
    expect(done?.type).toBe('done');
    expect(done).toMatchObject({ stopReason: 'end_turn', usage: { inputTokens: 10, outputTokens: 5 } });
    expect(calls).toHaveLength(1);
    expect(calls[0]).not.toHaveProperty('tools');
    expect(String(calls[0]!['system'])).toContain('Europe/Madrid');
  });

  it('ejecuta herramientas de servidor y continúa hasta la respuesta final', async () => {
    const { anthropic, calls } = scriptedAnthropic([
      {
        content: [{ type: 'tool_use', id: 'tu_1', name: 'echo', input: { text: 'hola' } }],
        stopReason: 'tool_use',
      },
      { text: ['Listo.'], content: [{ type: 'text', text: 'Listo.' }], stopReason: 'end_turn' },
    ]);
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([echoTool]), emit },
      { messages: [{ role: 'user', content: 'di hola' }], clientTools: [], context },
    );

    const secondCall = calls[1]!['messages'] as { role: string; content: unknown }[];
    expect(secondCall.at(-1)).toEqual({
      role: 'user',
      content: [{ type: 'tool_result', tool_use_id: 'tu_1', content: 'eco: hola' }],
    });
    expect(events.filter((e) => e.type === 'tool_status').map((e) => (e as { status: string }).status)).toEqual([
      'running',
      'done',
    ]);
    const done = events.at(-1) as Extract<ChatEvent, { type: 'done' }>;
    expect(done.type).toBe('done');
    expect(done.newMessages).toHaveLength(3); // assistant(tool_use), user(tool_result), assistant(texto)
    expect(done.usage.inputTokens).toBe(20);
  });

  it('devuelve el error de una herramienta a Claude sin romper el bucle', async () => {
    const { anthropic, calls } = scriptedAnthropic([
      { content: [{ type: 'tool_use', id: 'tu_1', name: 'boom', input: {} }], stopReason: 'tool_use' },
      { content: [{ type: 'text', text: 'No he podido.' }], stopReason: 'end_turn' },
    ]);
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([failingTool]), emit },
      { messages: [{ role: 'user', content: 'x' }], clientTools: [], context },
    );

    const results = (calls[1]!['messages'] as { content: { is_error?: boolean; content: string }[] }[]).at(-1)!.content;
    expect(results[0]).toMatchObject({ is_error: true });
    expect(results[0]!.content).toContain('fallo simulado');
    expect(events.some((e) => e.type === 'tool_status' && e.status === 'error')).toBe(true);
    expect(events.at(-1)?.type).toBe('done');
  });

  it('responde con error a una herramienta desconocida', async () => {
    const { anthropic, calls } = scriptedAnthropic([
      { content: [{ type: 'tool_use', id: 'tu_1', name: 'nope', input: {} }], stopReason: 'tool_use' },
      { content: [{ type: 'text', text: 'ok' }], stopReason: 'end_turn' },
    ]);
    const { emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([]), emit },
      { messages: [{ role: 'user', content: 'x' }], clientTools: [], context },
    );

    const results = (calls[1]!['messages'] as { content: { is_error?: boolean }[] }[]).at(-1)!.content;
    expect(results[0]).toMatchObject({ is_error: true });
  });

  it('cede el control a la app cuando Claude pide una herramienta del teléfono', async () => {
    const { anthropic, calls } = scriptedAnthropic([
      {
        text: ['Abro el calendario.'],
        content: [
          { type: 'text', text: 'Abro el calendario.' },
          { type: 'tool_use', id: 'tu_s', name: 'echo', input: { text: 'a' } },
          { type: 'tool_use', id: 'tu_c', name: 'open_app', input: { name: 'Calendar' } },
        ],
        stopReason: 'tool_use',
      },
    ]);
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([echoTool]), emit },
      { messages: [{ role: 'user', content: 'abre el calendario' }], clientTools: [openAppTool], context },
    );

    expect(calls).toHaveLength(1); // no vuelve a llamar a Claude hasta que la app responda
    const toolNames = (calls[0]!['tools'] as { name: string }[]).map((t) => t.name);
    expect(toolNames).toEqual(['echo', 'open_app']);

    const handoff = events.at(-1) as Extract<ChatEvent, { type: 'client_tool_calls' }>;
    expect(handoff.type).toBe('client_tool_calls');
    expect(handoff.calls).toEqual([{ id: 'tu_c', name: 'open_app', input: { name: 'Calendar' } }]);
    expect(handoff.serverResults).toEqual([{ type: 'tool_result', tool_use_id: 'tu_s', content: 'eco: a' }]);
    expect(handoff.newMessages).toHaveLength(1);
    expect(events.some((e) => e.type === 'done')).toBe(false);
  });

  it('se detiene al superar el máximo de iteraciones', async () => {
    const loop = { content: [{ type: 'tool_use', id: 'tu', name: 'echo', input: { text: 'x' } }], stopReason: 'tool_use' };
    const { anthropic, calls } = scriptedAnthropic([loop, loop, loop]);
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig({ maxToolIterations: 3 }), tools: new ToolRegistry([echoTool]), emit },
      { messages: [{ role: 'user', content: 'x' }], clientTools: [], context },
    );

    expect(calls).toHaveLength(3);
    expect(events.at(-1)).toMatchObject({ type: 'error', code: 'max_iterations' });
  });

  it('no llama a Claude si la petición ya está cancelada', async () => {
    const { anthropic, calls } = scriptedAnthropic([]);
    const controller = new AbortController();
    controller.abort();
    const { events, emit } = collect();

    await runChat(
      { anthropic, config: testConfig(), tools: new ToolRegistry([]), emit, signal: controller.signal },
      { messages: [{ role: 'user', content: 'x' }], clientTools: [], context },
    );

    expect(calls).toHaveLength(0);
    expect(events).toHaveLength(0);
  });
});

describe('describeError', () => {
  it('traduce estados HTTP de Anthropic a mensajes seguros', () => {
    expect(describeError({ status: 429 }).code).toBe('upstream_rate_limited');
    expect(describeError({ status: 529 }).code).toBe('upstream_unavailable');
    expect(describeError({ status: 500 }).code).toBe('upstream_unavailable');
    expect(describeError({ status: 401 }).code).toBe('upstream_auth');
    expect(describeError({ status: 400 }).code).toBe('bad_request');
    expect(describeError(new Error('secreto sk-ant-123'))).toEqual({
      code: 'internal',
      message: 'Ha ocurrido un error inesperado.',
    });
  });
});
