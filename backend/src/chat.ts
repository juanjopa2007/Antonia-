import type { Config } from './config.js';
import { buildSystemPrompt, type PromptContext } from './system.js';
import type { ToolDefinition, ToolRegistry } from './tools/index.js';

export interface ToolUseBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
  [key: string]: unknown;
}

export interface ContentBlock {
  type: string;
  [key: string]: unknown;
}

export interface MessageParam {
  role: 'user' | 'assistant';
  content: string | ContentBlock[];
}

export interface ToolResultBlock {
  type: 'tool_result';
  tool_use_id: string;
  content: string;
  is_error?: boolean;
  [key: string]: unknown;
}

/** Subconjunto del SDK de Anthropic que necesitamos; permite sustituirlo en los tests. */
export interface StreamLike {
  on(event: 'text', listener: (delta: string) => void): unknown;
  finalMessage(): Promise<{
    content: ContentBlock[];
    stop_reason: string | null;
    usage?: { input_tokens?: number; output_tokens?: number };
  }>;
  controller: { abort(): void };
}

export interface AnthropicLike {
  messages: {
    stream(params: Record<string, unknown>): StreamLike;
  };
}

export interface ClientToolCall {
  id: string;
  name: string;
  input: unknown;
}

export interface Usage {
  inputTokens: number;
  outputTokens: number;
}

export type ChatEvent =
  | { type: 'text'; delta: string }
  | { type: 'tool_status'; id: string; name: string; status: 'running' | 'done' | 'error' }
  | {
      type: 'client_tool_calls';
      /** Mensajes nuevos que la app debe añadir a su historial (el último es el del asistente con los tool_use). */
      newMessages: MessageParam[];
      /** Resultados de herramientas de servidor ya ejecutadas en esta misma vuelta. */
      serverResults: ToolResultBlock[];
      calls: ClientToolCall[];
      usage: Usage;
    }
  | { type: 'done'; stopReason: string | null; newMessages: MessageParam[]; usage: Usage }
  | { type: 'error'; code: string; message: string };

export interface ChatRequest {
  messages: MessageParam[];
  clientTools: ToolDefinition[];
  context: PromptContext;
}

export interface ChatDeps {
  anthropic: AnthropicLike;
  config: Config;
  tools: ToolRegistry;
  emit: (event: ChatEvent) => void;
  signal?: AbortSignal;
}

const MAX_TOOL_RESULT_CHARS = 20_000;

function isToolUse(block: ContentBlock): block is ToolUseBlock {
  return block.type === 'tool_use' && typeof block['id'] === 'string' && typeof block['name'] === 'string';
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}\n[resultado recortado]` : text;
}

function errorText(err: unknown): string {
  const message = err instanceof Error ? err.message : String(err);
  return truncate(`Error: ${message}`, 500);
}

/**
 * Bucle del agente. Ejecuta las herramientas de servidor y, cuando Claude pide una herramienta
 * del teléfono, se detiene y devuelve el control a la app (el servidor no guarda estado).
 */
export async function runChat(deps: ChatDeps, request: ChatRequest): Promise<void> {
  const { anthropic, config, tools, emit, signal } = deps;

  const system = buildSystemPrompt(request.context);
  const toolDefinitions = [...tools.definitions(), ...request.clientTools];
  const clientToolNames = new Set(request.clientTools.map((t) => t.name));

  const messages: MessageParam[] = [...request.messages];
  const newMessages: MessageParam[] = [];
  const usage: Usage = { inputTokens: 0, outputTokens: 0 };

  for (let iteration = 0; iteration < config.maxToolIterations; iteration++) {
    if (signal?.aborted) return;

    const stream = anthropic.messages.stream({
      model: config.model,
      max_tokens: config.maxTokens,
      system,
      messages,
      ...(toolDefinitions.length > 0 ? { tools: toolDefinitions } : {}),
    });

    const onAbort = () => stream.controller.abort();
    signal?.addEventListener('abort', onAbort, { once: true });
    stream.on('text', (delta) => emit({ type: 'text', delta }));

    let final: Awaited<ReturnType<StreamLike['finalMessage']>>;
    try {
      final = await stream.finalMessage();
    } finally {
      signal?.removeEventListener('abort', onAbort);
    }

    usage.inputTokens += final.usage?.input_tokens ?? 0;
    usage.outputTokens += final.usage?.output_tokens ?? 0;

    const assistantMessage: MessageParam = { role: 'assistant', content: final.content };
    messages.push(assistantMessage);
    newMessages.push(assistantMessage);

    const toolUses = final.content.filter(isToolUse);
    if (final.stop_reason !== 'tool_use' || toolUses.length === 0) {
      emit({ type: 'done', stopReason: final.stop_reason, newMessages, usage });
      return;
    }

    const serverCalls = toolUses.filter((t) => !clientToolNames.has(t.name));
    const clientCalls = toolUses.filter((t) => clientToolNames.has(t.name));

    const serverResults = await Promise.all(
      serverCalls.map(async (call): Promise<ToolResultBlock> => {
        emit({ type: 'tool_status', id: call.id, name: call.name, status: 'running' });
        const tool = tools.get(call.name);
        if (!tool) {
          emit({ type: 'tool_status', id: call.id, name: call.name, status: 'error' });
          return {
            type: 'tool_result',
            tool_use_id: call.id,
            content: `Error: herramienta desconocida "${call.name}"`,
            is_error: true,
          };
        }
        try {
          const output = await tool.execute(call.input);
          emit({ type: 'tool_status', id: call.id, name: call.name, status: 'done' });
          return { type: 'tool_result', tool_use_id: call.id, content: truncate(output, MAX_TOOL_RESULT_CHARS) };
        } catch (err) {
          emit({ type: 'tool_status', id: call.id, name: call.name, status: 'error' });
          return { type: 'tool_result', tool_use_id: call.id, content: errorText(err), is_error: true };
        }
      }),
    );

    if (clientCalls.length > 0) {
      emit({
        type: 'client_tool_calls',
        newMessages,
        serverResults,
        calls: clientCalls.map((c) => ({ id: c.id, name: c.name, input: c.input })),
        usage,
      });
      return;
    }

    const resultsMessage: MessageParam = { role: 'user', content: serverResults };
    messages.push(resultsMessage);
    newMessages.push(resultsMessage);
  }

  emit({
    type: 'error',
    code: 'max_iterations',
    message: 'La tarea necesitó demasiados pasos y la detuve. ¿Quieres que lo intente de otra forma?',
  });
}

export function describeError(err: unknown): { code: string; message: string } {
  const status =
    typeof err === 'object' && err !== null && 'status' in err && typeof (err as { status: unknown }).status === 'number'
      ? (err as { status: number }).status
      : undefined;

  if (status === 400) {
    return { code: 'bad_request', message: 'No he podido procesar esa conversación.' };
  }
  if (status === 401 || status === 403) {
    return { code: 'upstream_auth', message: 'El servidor no puede acceder a Claude en este momento.' };
  }
  if (status === 429) {
    return { code: 'upstream_rate_limited', message: 'Claude tiene demasiadas peticiones ahora mismo. Inténtalo en unos segundos.' };
  }
  if (status !== undefined && (status >= 500 || status === 529)) {
    return { code: 'upstream_unavailable', message: 'Claude no está disponible en este momento. Inténtalo de nuevo en un rato.' };
  }
  return { code: 'internal', message: 'Ha ocurrido un error inesperado.' };
}
