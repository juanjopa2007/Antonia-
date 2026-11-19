import Anthropic from '@anthropic-ai/sdk';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import type { AnthropicLike } from '../src/chat.js';
import { TOKEN, testConfig } from './helpers.js';

/**
 * Prueba de integración con el SDK real de Anthropic apuntando a un servidor local que
 * emite el formato SSE de la API de mensajes. Verifica que nuestra interfaz AnthropicLike
 * coincide con el SDK de verdad (on('text'), finalMessage, controller).
 */

function sse(events: { type: string; [k: string]: unknown }[]): string {
  return events.map((e) => `event: ${e.type}\ndata: ${JSON.stringify(e)}\n\n`).join('');
}

const message = (stop: string | null) => ({
  id: 'msg_1',
  type: 'message',
  role: 'assistant',
  model: 'claude-sonnet-5-5',
  content: [],
  stop_reason: stop,
  stop_sequence: null,
  usage: { input_tokens: 12, output_tokens: 1 },
});

const toolUseTurn = sse([
  { type: 'message_start', message: message(null) },
  { type: 'content_block_start', index: 0, content_block: { type: 'tool_use', id: 'toolu_1', name: 'calculator', input: {} } },
  { type: 'content_block_delta', index: 0, delta: { type: 'input_json_delta', partial_json: '{"expression":' } },
  { type: 'content_block_delta', index: 0, delta: { type: 'input_json_delta', partial_json: '"6*7"}' } },
  { type: 'content_block_stop', index: 0 },
  { type: 'message_delta', delta: { stop_reason: 'tool_use', stop_sequence: null }, usage: { output_tokens: 20 } },
  { type: 'message_stop' },
]);

const textTurn = sse([
  { type: 'message_start', message: message(null) },
  { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } },
  { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'Son ' } },
  { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'cuarenta y dos.' } },
  { type: 'content_block_stop', index: 0 },
  { type: 'message_delta', delta: { stop_reason: 'end_turn', stop_sequence: null }, usage: { output_tokens: 8 } },
  { type: 'message_stop' },
]);

let fake: Server;
let baseURL: string;
const received: { messages: { role: string; content: unknown }[] }[] = [];

beforeAll(async () => {
  let turn = 0;
  fake = createServer((req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      received.push(JSON.parse(body));
      res.writeHead(200, { 'content-type': 'text/event-stream' });
      res.end(turn++ === 0 ? toolUseTurn : textTurn);
    });
  });
  await new Promise<void>((resolve) => fake.listen(0, '127.0.0.1', resolve));
  baseURL = `http://127.0.0.1:${(fake.address() as AddressInfo).port}`;
});

afterAll(async () => {
  await new Promise((resolve) => fake.close(resolve));
});

describe('SDK real de Anthropic', () => {
  it('encaja con AnthropicLike: streaming, tool use y continuación', async () => {
    const client = new Anthropic({ apiKey: 'sk-test', baseURL, maxRetries: 0 });
    const app = await buildApp({ config: testConfig(), anthropic: client as unknown as AnthropicLike });

    const res = await app.inject({
      method: 'POST',
      url: '/v1/chat',
      headers: { authorization: `Bearer ${TOKEN}` },
      payload: {
        messages: [{ role: 'user', content: '¿Cuánto es 6 por 7?' }],
        clientTools: [],
        context: { now: '2026-11-19T09:30:00+01:00', timezone: 'Europe/Madrid' },
      },
    });
    await app.close();

    expect(res.statusCode).toBe(200);
    const kinds = res.body
      .split('\n\n')
      .filter((b) => b.startsWith('event:'))
      .map((b) => b.split('\n')[0]!.slice(7));
    expect(kinds).toEqual(['tool_status', 'tool_status', 'text', 'text', 'done']);
    expect(res.body).toContain('cuarenta y dos.');

    // La segunda petición a Claude lleva el resultado de la calculadora (6*7 = 42).
    expect(received).toHaveLength(2);
    const last = received[1]!.messages.at(-1)!;
    expect(last.role).toBe('user');
    expect(JSON.stringify(last.content)).toContain('"content":"42"');
    expect(JSON.stringify(received[1]!.messages)).toContain('"expression":"6*7"');
  });
});
