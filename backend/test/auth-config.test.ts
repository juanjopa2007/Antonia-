import { describe, expect, it } from 'vitest';
import { extractBearer, makeTokenVerifier } from '../src/auth.js';
import { loadConfig } from '../src/config.js';

const TOKEN_A = 'a'.repeat(32);
const TOKEN_B = 'b'.repeat(32);

describe('autenticación', () => {
  it('extrae el token Bearer', () => {
    expect(extractBearer('Bearer abc')).toBe('abc');
    expect(extractBearer('bearer abc')).toBe('abc');
    expect(extractBearer('Basic abc')).toBeUndefined();
    expect(extractBearer('Bearer')).toBeUndefined();
    expect(extractBearer(undefined)).toBeUndefined();
  });

  it('acepta solo tokens configurados y devuelve un id que no es el token', () => {
    const verify = makeTokenVerifier([TOKEN_A, TOKEN_B]);
    const idA = verify(TOKEN_A);
    const idB = verify(TOKEN_B);
    expect(idA).toBeTruthy();
    expect(idB).toBeTruthy();
    expect(idA).not.toBe(idB);
    expect(idA).not.toContain(TOKEN_A);
    expect(verify('c'.repeat(32))).toBeNull();
    expect(verify('')).toBeNull();
    expect(verify(undefined)).toBeNull();
  });
});

describe('configuración', () => {
  const base = { ANTHROPIC_API_KEY: 'sk-test', APP_TOKENS: TOKEN_A };

  it('aplica valores por defecto', () => {
    const c = loadConfig(base);
    expect(c.model).toBe('claude-sonnet-5-5');
    expect(c.port).toBe(8080);
    expect(c.maxToolIterations).toBe(8);
    expect(c.braveApiKey).toBeUndefined();
  });

  it('separa varios tokens', () => {
    expect(loadConfig({ ...base, APP_TOKENS: `${TOKEN_A}, ${TOKEN_B}` }).appTokens).toEqual([TOKEN_A, TOKEN_B]);
  });

  it('exige clave de Anthropic y tokens largos', () => {
    expect(() => loadConfig({ APP_TOKENS: TOKEN_A })).toThrow(/ANTHROPIC_API_KEY/);
    expect(() => loadConfig({ ...base, APP_TOKENS: 'corto' })).toThrow(/24 caracteres/);
    expect(() => loadConfig({ ...base, APP_TOKENS: ' , ' })).toThrow();
  });

  it('valida rangos numéricos', () => {
    expect(() => loadConfig({ ...base, PORT: '99999' })).toThrow();
    expect(() => loadConfig({ ...base, MAX_TOOL_ITERATIONS: '0' })).toThrow();
  });
});
