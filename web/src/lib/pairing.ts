/**
 * Mirror of the phone's PairingCode: what a person types, tidied the same way
 * the server normalises it before hashing (upper-case, no spaces or dashes).
 * The database rejects anything that is not a live code; this only keeps
 * obviously wrong input from making a round trip.
 */
export const PAIRING_CODE_LENGTH = 6;
export const PAIRING_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

export function normalizePairingCode(input: string): string {
  return input.trim().toUpperCase().replace(/[\s-]/g, '');
}

export function isWellFormedPairingCode(code: string): boolean {
  return code.length === PAIRING_CODE_LENGTH && [...code].every((c) => PAIRING_ALPHABET.includes(c));
}

/** A fresh code from the same alphabet as the phone, from the browser's CSPRNG. */
export function generatePairingCode(): string {
  const bytes = new Uint8Array(PAIRING_CODE_LENGTH);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => PAIRING_ALPHABET[b % PAIRING_ALPHABET.length]).join('');
}

/** Lower-case hex SHA-256 of the normalised code: what the server stores and compares. */
export async function hashPairingCode(code: string): Promise<string> {
  const data = new TextEncoder().encode(normalizePairingCode(code));
  const digest = await crypto.subtle.digest('SHA-256', data);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export const PAIRING_VALIDITY_MINUTES = 15;
