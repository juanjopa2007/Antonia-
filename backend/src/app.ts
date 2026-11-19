import Fastify, { type FastifyInstance, type FastifyReply, type FastifyRequest } from 'fastify';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import { z } from 'zod';
import { extractBearer, hashForKey, makeTokenVerifier } from './auth.js';
import { describeError, runChat, type AnthropicLike, type ChatEvent } from './chat.js';
import type { Config } from './config.js';
import { createServerTools, type ToolRegistry } from './tools/index.js';

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string;
  }
}

const clientToolSchema = z.object({
  name: z.string().regex(/^[a-zA-Z0-9_-]{1,64}$/),
  description: z.string().min(1).max(2000),
  input_schema: z.looseObject({ type: z.literal('object') }),
});

const messageSchema = z.object({
  role: z.enum(['user', 'assistant']),
  content: z.union([z.string().max(50_000), z.array(z.record(z.string(), z.unknown()).and(z.object({ type: z.string() }))).max(100)]),
});

const bodySchema = z.object({
  messages: z
    .array(messageSchema)
    .min(1)
    .max(100)
    .refine((m) => m[m.length - 1]?.role === 'user', 'El último mensaje debe ser del usuario'),
  clientTools: z.array(clientToolSchema).max(40).default([]),
  context: z.object({
    now: z.string().refine((s) => !Number.isNaN(Date.parse(s)), 'Fecha no válida'),
    timezone: z.string().min(1).max(64),
    assistantName: z.string().min(1).max(40).optional(),
  }),
});

export interface AppDeps {
  config: Config;
  anthropic: AnthropicLike;
  fetchImpl?: typeof fetch;
  logger?: boolean;
  /** Para tests: permite inyectar un registro de herramientas propio. */
  tools?: ToolRegistry;
}

export async function buildApp(deps: AppDeps): Promise<FastifyInstance> {
  const { config, anthropic } = deps;
  const tools = deps.tools ?? createServerTools(config, deps.fetchImpl);
  const verify = makeTokenVerifier(config.appTokens);

  const app = Fastify({
    logger: deps.logger ?? false,
    trustProxy: true, // Cloud Run está detrás de un proxy: así req.ip es la IP real del cliente.
    bodyLimit: 1024 * 1024,
  });

  await app.register(helmet);
  await app.register(rateLimit, { global: false });

  async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const userId = verify(extractBearer(request.headers.authorization));
    if (!userId) {
      await reply.code(401).header('WWW-Authenticate', 'Bearer').send({ error: 'unauthorized' });
      return;
    }
    request.userId = userId;
  }

  app.get('/healthz', async () => ({ ok: true }));

  app.post(
    '/v1/chat',
    {
      onRequest: [authenticate],
      config: {
        rateLimit: {
          max: config.rateLimitPerMinute,
          timeWindow: '1 minute',
          keyGenerator: (req: FastifyRequest) => hashForKey(req.headers.authorization ?? req.ip),
        },
      },
    },
    async (request, reply) => {
      const parsed = bodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'invalid_request',
          details: parsed.error.issues.map((i) => `${i.path.join('.') || '(raíz)'}: ${i.message}`),
        });
      }
      const body = parsed.data;

      const names = new Set<string>();
      for (const tool of body.clientTools) {
        if (tools.has(tool.name) || names.has(tool.name)) {
          return reply.code(400).send({
            error: 'invalid_request',
            details: [`clientTools: el nombre "${tool.name}" está repetido o choca con una herramienta del servidor`],
          });
        }
        names.add(tool.name);
      }

      // A partir de aquí la respuesta es un flujo SSE.
      reply.hijack();
      const raw = reply.raw;
      raw.writeHead(200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      });

      const controller = new AbortController();
      raw.on('close', () => {
        if (!raw.writableEnded) controller.abort();
      });
      const heartbeat = setInterval(() => {
        if (!raw.writableEnded) raw.write(': ping\n\n');
      }, 15_000);

      const emit = (event: ChatEvent): void => {
        if (raw.writableEnded || raw.destroyed) return;
        raw.write(`event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`);
      };

      try {
        await runChat(
          { anthropic, config, tools, emit, signal: controller.signal },
          {
            messages: body.messages,
            clientTools: body.clientTools,
            context: body.context,
          },
        );
      } catch (err) {
        if (!controller.signal.aborted) {
          request.log.error({ err, userId: request.userId }, 'error en /v1/chat');
          emit({ type: 'error', ...describeError(err) });
        }
      } finally {
        clearInterval(heartbeat);
        raw.end();
      }
    },
  );

  return app;
}
