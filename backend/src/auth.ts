import { createHash, timingSafeEqual } from 'node:crypto';

export function extractBearer(header: string | undefined): string | undefined {
  if (!header) return undefined;
  const match = /^Bearer\s+(\S+)$/i.exec(header.trim());
  return match?.[1];
}

function sha256(value: string): Buffer {
  return createHash('sha256').update(value).digest();
}

/**
 * Devuelve una función que valida un token en tiempo constante y, si es válido,
 * devuelve un identificador estable (prefijo del hash) que nunca expone el token.
 */
export function makeTokenVerifier(tokens: string[]): (candidate: string | undefined) => string | null {
  const entries = tokens.map((token) => {
    const hash = sha256(token);
    return { hash, id: hash.toString('hex').slice(0, 12) };
  });

  return (candidate) => {
    if (!candidate) return null;
    const candidateHash = sha256(candidate);
    let matched: string | null = null;
    // Se recorren todos para no filtrar cuál coincidió por tiempo de respuesta.
    for (const entry of entries) {
      if (timingSafeEqual(candidateHash, entry.hash) && matched === null) {
        matched = entry.id;
      }
    }
    return matched;
  };
}

export function hashForKey(value: string): string {
  return sha256(value).toString('hex').slice(0, 16);
}
