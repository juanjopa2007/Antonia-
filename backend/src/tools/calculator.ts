import { z } from 'zod';
import type { ServerTool } from './types.js';

/**
 * Evaluador aritmético propio (descenso recursivo). No usa eval ni Function.
 * Soporta + - * / % ^ **, paréntesis, constantes y funciones de una lista blanca.
 */

const MAX_LENGTH = 200;
const MAX_DEPTH = 40;

type Token =
  | { kind: 'num'; value: number }
  | { kind: 'id'; value: string }
  | { kind: 'op'; value: string };

const CONSTANTS = new Map<string, number>([
  ['pi', Math.PI],
  ['e', Math.E],
]);

const FUNCTIONS = new Map<string, { arity: number | 'variadic'; fn: (...a: number[]) => number }>([
  ['sqrt', { arity: 1, fn: Math.sqrt }],
  ['abs', { arity: 1, fn: Math.abs }],
  ['sin', { arity: 1, fn: Math.sin }],
  ['cos', { arity: 1, fn: Math.cos }],
  ['tan', { arity: 1, fn: Math.tan }],
  ['ln', { arity: 1, fn: Math.log }],
  ['log', { arity: 1, fn: Math.log10 }],
  ['exp', { arity: 1, fn: Math.exp }],
  ['floor', { arity: 1, fn: Math.floor }],
  ['ceil', { arity: 1, fn: Math.ceil }],
  ['round', { arity: 1, fn: Math.round }],
  ['pow', { arity: 2, fn: Math.pow }],
  ['min', { arity: 'variadic', fn: (...a) => Math.min(...a) }],
  ['max', { arity: 'variadic', fn: (...a) => Math.max(...a) }],
]);

function tokenize(input: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < input.length) {
    const ch = input[i]!;
    if (/\s/.test(ch)) {
      i++;
      continue;
    }
    if (/[0-9.]/.test(ch)) {
      const match = /^(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?/.exec(input.slice(i));
      if (!match) throw new Error(`Número no válido en la posición ${i + 1}`);
      tokens.push({ kind: 'num', value: Number(match[0]) });
      i += match[0].length;
      continue;
    }
    if (/[a-zA-Z_]/.test(ch)) {
      const match = /^[a-zA-Z_][a-zA-Z0-9_]*/.exec(input.slice(i))!;
      tokens.push({ kind: 'id', value: match[0].toLowerCase() });
      i += match[0].length;
      continue;
    }
    if (ch === '*' && input[i + 1] === '*') {
      tokens.push({ kind: 'op', value: '^' });
      i += 2;
      continue;
    }
    if ('+-*/%^(),'.includes(ch)) {
      tokens.push({ kind: 'op', value: ch });
      i++;
      continue;
    }
    throw new Error(`Carácter no permitido: "${ch}"`);
  }
  return tokens;
}

class Parser {
  private pos = 0;
  private depth = 0;

  constructor(private readonly tokens: Token[]) {}

  parse(): number {
    if (this.tokens.length === 0) throw new Error('La expresión está vacía');
    const value = this.expression();
    if (this.pos < this.tokens.length) {
      throw new Error('Sobran elementos al final de la expresión');
    }
    return value;
  }

  private peek(): Token | undefined {
    return this.tokens[this.pos];
  }

  private isOp(value: string): boolean {
    const t = this.peek();
    return t?.kind === 'op' && t.value === value;
  }

  private enter(): void {
    if (++this.depth > MAX_DEPTH) throw new Error('Expresión demasiado anidada');
  }

  private leave(): void {
    this.depth--;
  }

  private expression(): number {
    let left = this.term();
    while (this.isOp('+') || this.isOp('-')) {
      const op = (this.tokens[this.pos++] as { value: string }).value;
      const right = this.term();
      left = op === '+' ? left + right : left - right;
    }
    return left;
  }

  private term(): number {
    let left = this.unary();
    while (this.isOp('*') || this.isOp('/') || this.isOp('%')) {
      const op = (this.tokens[this.pos++] as { value: string }).value;
      const right = this.unary();
      if ((op === '/' || op === '%') && right === 0) {
        throw new Error('División entre cero');
      }
      left = op === '*' ? left * right : op === '/' ? left / right : left % right;
    }
    return left;
  }

  private unary(): number {
    if (this.isOp('-')) {
      this.pos++;
      this.enter();
      try {
        return -this.unary();
      } finally {
        this.leave();
      }
    }
    if (this.isOp('+')) {
      this.pos++;
      this.enter();
      try {
        return this.unary();
      } finally {
        this.leave();
      }
    }
    return this.power();
  }

  private power(): number {
    const base = this.primary();
    if (this.isOp('^')) {
      this.pos++;
      this.enter();
      try {
        // Asociativa por la derecha: 2^3^2 = 2^(3^2)
        return Math.pow(base, this.unary());
      } finally {
        this.leave();
      }
    }
    return base;
  }

  private primary(): number {
    const token = this.peek();
    if (!token) throw new Error('La expresión termina de forma inesperada');

    if (token.kind === 'num') {
      this.pos++;
      return token.value;
    }

    if (token.kind === 'id') {
      this.pos++;
      if (this.isOp('(')) {
        const spec = FUNCTIONS.get(token.value);
        if (!spec) throw new Error(`Función desconocida: ${token.value}`);
        this.pos++;
        this.enter();
        try {
          const args: number[] = [];
          if (!this.isOp(')')) {
            args.push(this.expression());
            while (this.isOp(',')) {
              this.pos++;
              args.push(this.expression());
            }
          }
          if (!this.isOp(')')) throw new Error('Falta cerrar un paréntesis');
          this.pos++;
          if (spec.arity === 'variadic') {
            if (args.length === 0) throw new Error(`${token.value} necesita al menos un argumento`);
          } else if (args.length !== spec.arity) {
            throw new Error(`${token.value} necesita ${spec.arity} argumento(s)`);
          }
          return spec.fn(...args);
        } finally {
          this.leave();
        }
      }
      const constant = CONSTANTS.get(token.value);
      if (constant === undefined) throw new Error(`Nombre desconocido: ${token.value}`);
      return constant;
    }

    if (token.value === '(') {
      this.pos++;
      this.enter();
      try {
        const value = this.expression();
        if (!this.isOp(')')) throw new Error('Falta cerrar un paréntesis');
        this.pos++;
        return value;
      } finally {
        this.leave();
      }
    }

    throw new Error(`Símbolo inesperado: "${token.value}"`);
  }
}

export function evaluate(expression: string): number {
  if (expression.length > MAX_LENGTH) {
    throw new Error(`La expresión supera los ${MAX_LENGTH} caracteres`);
  }
  const result = new Parser(tokenize(expression)).parse();
  if (!Number.isFinite(result)) {
    throw new Error('El resultado no es un número finito');
  }
  return result;
}

export function formatNumber(value: number): string {
  // 12 cifras significativas elimina ruido de coma flotante (0.1 + 0.2).
  return String(Number(value.toPrecision(12)));
}

const inputSchema = z.object({ expression: z.string().min(1) });

export const calculatorTool: ServerTool = {
  definition: {
    name: 'calculator',
    description:
      'Calcula expresiones aritméticas con exactitud. Operadores: + - * / % (resto) ^ (potencia), paréntesis, ' +
      'constantes pi y e, y funciones sqrt, abs, sin, cos, tan (radianes), ln, log (base 10), exp, floor, ceil, round, ' +
      'pow, min, max. Usa punto como separador decimal. Para porcentajes, convierte: "15% de 80" = 0.15 * 80. ' +
      'Úsala siempre que el cálculo no sea trivial en lugar de hacerlo de cabeza.',
    input_schema: {
      type: 'object',
      properties: {
        expression: { type: 'string', description: 'Expresión a evaluar, por ejemplo "(1200 * 1.21) / 12".' },
      },
      required: ['expression'],
    },
  },
  async execute(input) {
    const { expression } = inputSchema.parse(input);
    return formatNumber(evaluate(expression));
  },
};
