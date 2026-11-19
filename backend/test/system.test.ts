import { describe, expect, it } from 'vitest';
import { buildSystemPrompt } from '../src/system.js';

const base = { now: '2026-11-19T09:30:00+01:00', timezone: 'Europe/Madrid' };

describe('prompt de sistema', () => {
  it('usa el nombre Antonia por defecto', () => {
    const prompt = buildSystemPrompt(base);
    expect(prompt).toContain('Eres Antonia');
    expect(prompt).toContain('te llamas Antonia');
  });

  it('permite cambiar el nombre desde la app', () => {
    expect(buildSystemPrompt({ ...base, assistantName: 'Friday' })).toContain('Eres Friday');
  });

  it('ignora un nombre vacío', () => {
    expect(buildSystemPrompt({ ...base, assistantName: '   ' })).toContain('Eres Antonia');
  });

  it('incluye la fecha en español y la zona horaria', () => {
    const prompt = buildSystemPrompt(base);
    expect(prompt).toContain('jueves, 19 de noviembre de 2026');
    expect(prompt).toContain('Europe/Madrid');
  });

  it('no falla con una zona horaria inválida', () => {
    expect(() => buildSystemPrompt({ ...base, timezone: 'No/Existe' })).not.toThrow();
  });
});
