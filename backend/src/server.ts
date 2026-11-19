import Anthropic from '@anthropic-ai/sdk';
import { buildApp } from './app.js';
import type { AnthropicLike } from './chat.js';
import { loadConfig } from './config.js';

const config = loadConfig();
const anthropic = new Anthropic({ apiKey: config.anthropicApiKey });

const app = await buildApp({
  config,
  anthropic: anthropic as unknown as AnthropicLike,
  logger: true,
});

// Cloud Run envía SIGTERM antes de parar la instancia.
for (const signal of ['SIGTERM', 'SIGINT'] as const) {
  process.on(signal, () => {
    app.log.info({ signal }, 'cerrando servidor');
    app.close().then(
      () => process.exit(0),
      () => process.exit(1),
    );
  });
}

await app.listen({ port: config.port, host: config.host });
