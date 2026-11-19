import type { Config } from '../config.js';
import { calculatorTool } from './calculator.js';
import { ToolRegistry, type ServerTool } from './types.js';
import { createWebSearchTool } from './webSearch.js';

export { ToolRegistry } from './types.js';
export type { ServerTool, ToolDefinition } from './types.js';

/**
 * Registra solo las herramientas que realmente se pueden ejecutar con la configuración actual.
 * Si falta BRAVE_API_KEY, Claude ni siquiera ve la herramienta de búsqueda.
 */
export function createServerTools(config: Config, fetchImpl: typeof fetch = fetch): ToolRegistry {
  const tools: ServerTool[] = [calculatorTool];
  if (config.braveApiKey) {
    tools.push(createWebSearchTool(config.braveApiKey, fetchImpl));
  }
  return new ToolRegistry(tools);
}
