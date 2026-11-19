import type { AnthropicLike, ContentBlock, StreamLike } from '../src/chat.js';
import type { Config } from '../src/config.js';

export const TOKEN = 'test-token-0123456789-abcdefghij';

export function testConfig(overrides: Partial<Config> = {}): Config {
  return {
    anthropicApiKey: 'sk-test',
    appTokens: [TOKEN],
    model: 'claude-sonnet-5-5',
    maxTokens: 512,
    maxToolIterations: 4,
    rateLimitPerMinute: 100,
    port: 0,
    host: '127.0.0.1',
    ...overrides,
  };
}

export interface ScriptedTurn {
  text?: string[];
  content: ContentBlock[];
  stopReason: string;
  error?: unknown;
}

/** Cliente de Anthropic simulado: cada llamada a stream() consume el siguiente turno del guion. */
export function scriptedAnthropic(turns: ScriptedTurn[]) {
  const calls: Record<string, unknown>[] = [];
  let index = 0;

  const anthropic: AnthropicLike = {
    messages: {
      stream(params) {
        // Copia profunda: el bucle sigue añadiendo mensajes al mismo array.
        calls.push(JSON.parse(JSON.stringify(params)) as Record<string, unknown>);
        const turn = turns[index++];
        if (!turn) throw new Error('Se hicieron más llamadas a Claude de las previstas en el guion');

        const listeners: ((d: string) => void)[] = [];
        const stream: StreamLike = {
          on(_event, listener) {
            listeners.push(listener);
            return stream;
          },
          async finalMessage() {
            if (turn.error) throw turn.error;
            for (const delta of turn.text ?? []) listeners.forEach((l) => l(delta));
            return {
              content: turn.content,
              stop_reason: turn.stopReason,
              usage: { input_tokens: 10, output_tokens: 5 },
            };
          },
          controller: { abort() {} },
        };
        return stream;
      },
    },
  };

  return { anthropic, calls };
}
