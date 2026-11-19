import { z } from 'zod';

const envSchema = z.object({
  ANTHROPIC_API_KEY: z.string().min(1, 'ANTHROPIC_API_KEY es obligatoria'),
  // Tokens de acceso de la app, separados por comas. Mínimo 24 caracteres cada uno.
  APP_TOKENS: z.string().min(1, 'APP_TOKENS es obligatoria'),
  MODEL: z.string().min(1).default('claude-sonnet-5-5'),
  MAX_TOKENS: z.coerce.number().int().min(64).max(8192).default(1024),
  MAX_TOOL_ITERATIONS: z.coerce.number().int().min(1).max(15).default(8),
  BRAVE_API_KEY: z.string().optional(),
  RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(1).max(600).default(30),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  HOST: z.string().min(1).default('0.0.0.0'),
});

export interface Config {
  anthropicApiKey: string;
  appTokens: string[];
  model: string;
  maxTokens: number;
  maxToolIterations: number;
  braveApiKey?: string;
  rateLimitPerMinute: number;
  port: number;
  host: string;
}

export function loadConfig(env: Record<string, string | undefined> = process.env): Config {
  const parsed = envSchema.safeParse(env);
  if (!parsed.success) {
    const detail = parsed.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new Error(`Configuración inválida: ${detail}`);
  }
  const e = parsed.data;

  const appTokens = e.APP_TOKENS.split(',')
    .map((t) => t.trim())
    .filter(Boolean);
  if (appTokens.length === 0) {
    throw new Error('Configuración inválida: APP_TOKENS no contiene ningún token');
  }
  if (appTokens.some((t) => t.length < 24)) {
    throw new Error('Configuración inválida: cada token de APP_TOKENS debe tener al menos 24 caracteres');
  }

  const braveApiKey = e.BRAVE_API_KEY?.trim();

  return {
    anthropicApiKey: e.ANTHROPIC_API_KEY,
    appTokens,
    model: e.MODEL,
    maxTokens: e.MAX_TOKENS,
    maxToolIterations: e.MAX_TOOL_ITERATIONS,
    ...(braveApiKey ? { braveApiKey } : {}),
    rateLimitPerMinute: e.RATE_LIMIT_PER_MINUTE,
    port: e.PORT,
    host: e.HOST,
  };
}
