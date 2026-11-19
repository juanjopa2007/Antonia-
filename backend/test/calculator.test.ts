import { describe, expect, it } from 'vitest';
import { calculatorTool, evaluate, formatNumber } from '../src/tools/calculator.js';

describe('calculadora', () => {
  it('respeta la precedencia de operadores', () => {
    expect(evaluate('2 + 3 * 4')).toBe(14);
    expect(evaluate('(2 + 3) * 4')).toBe(20);
  });

  it('soporta potencias asociativas por la derecha', () => {
    expect(evaluate('2^3^2')).toBe(512);
    expect(evaluate('2**10')).toBe(1024);
  });

  it('soporta negativos y unarios', () => {
    expect(evaluate('-3 + 5')).toBe(2);
    expect(evaluate('--4')).toBe(4);
    expect(evaluate('2 * -3')).toBe(-6);
  });

  it('soporta funciones y constantes', () => {
    expect(evaluate('sqrt(144)')).toBe(12);
    expect(evaluate('max(1, 7, 3)')).toBe(7);
    expect(evaluate('pow(2, 8)')).toBe(256);
    expect(evaluate('pi')).toBeCloseTo(Math.PI);
  });

  it('calcula resto y decimales', () => {
    expect(evaluate('10 % 3')).toBe(1);
    expect(evaluate('1200 * 1.21 / 12')).toBeCloseTo(121);
  });

  it('elimina ruido de coma flotante al formatear', () => {
    expect(formatNumber(evaluate('0.1 + 0.2'))).toBe('0.3');
  });

  it('rechaza división entre cero', () => {
    expect(() => evaluate('5 / 0')).toThrow(/cero/);
    expect(() => evaluate('5 % 0')).toThrow(/cero/);
  });

  it('rechaza código y nombres no permitidos', () => {
    expect(() => evaluate('process.exit(1)')).toThrow();
    expect(() => evaluate('require("fs")')).toThrow();
    expect(() => evaluate('constructor')).toThrow(/desconocido/);
    expect(() => evaluate('foo(1)')).toThrow(/desconocida/);
  });

  it('rechaza expresiones mal formadas', () => {
    expect(() => evaluate('')).toThrow();
    expect(() => evaluate('(1 + 2')).toThrow(/paréntesis/);
    expect(() => evaluate('1 +')).toThrow();
    expect(() => evaluate('1 2')).toThrow();
    expect(() => evaluate('sqrt(1, 2)')).toThrow(/argumento/);
  });

  it('rechaza resultados no finitos y entradas excesivas', () => {
    expect(() => evaluate('10^1000')).toThrow(/finito/);
    expect(() => evaluate('1+'.repeat(150) + '1')).toThrow(/supera/);
    expect(() => evaluate('('.repeat(60) + '1' + ')'.repeat(60))).toThrow(/anidada/);
  });

  it('la herramienta devuelve texto y propaga errores', async () => {
    await expect(calculatorTool.execute({ expression: '15 * 80 / 100' })).resolves.toBe('12');
    await expect(calculatorTool.execute({ expression: '1/0' })).rejects.toThrow();
    await expect(calculatorTool.execute({})).rejects.toThrow();
  });
});
