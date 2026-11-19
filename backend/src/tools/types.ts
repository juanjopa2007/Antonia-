export interface ToolDefinition {
  name: string;
  description: string;
  input_schema: {
    type: 'object';
    properties?: Record<string, unknown>;
    required?: string[];
    [key: string]: unknown;
  };
}

export interface ServerTool {
  definition: ToolDefinition;
  /** Devuelve el texto que se enviará a Claude como resultado. Si lanza, se envía como error. */
  execute(input: unknown): Promise<string>;
}

export class ToolRegistry {
  private readonly tools = new Map<string, ServerTool>();

  constructor(tools: ServerTool[]) {
    for (const tool of tools) {
      if (this.tools.has(tool.definition.name)) {
        throw new Error(`Herramienta duplicada: ${tool.definition.name}`);
      }
      this.tools.set(tool.definition.name, tool);
    }
  }

  has(name: string): boolean {
    return this.tools.has(name);
  }

  get(name: string): ServerTool | undefined {
    return this.tools.get(name);
  }

  definitions(): ToolDefinition[] {
    return [...this.tools.values()].map((t) => t.definition);
  }
}
