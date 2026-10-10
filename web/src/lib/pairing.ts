/**
 * Mirror of the phone's PairingCode: what a person types, tidied the same way
 * the server normalises it before hashing (upper-case, no spaces or dashes).
 * The database rejects anything that is not a live code; this only keeps
 * obviously wrong input from making a round trip.
 */
export const PAIRING_CODE_LENGTH = 6;
export const PAIRING_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/**
 * Cyrillic capitals drawn like the code's Latin letters, and those letters.
 * Codes are Latin, but a partner on a Ukrainian, Belarusian or Russian
 * keyboard types "Н46МСW" for H46MCW without seeing a difference.
 */
const CYRILLIC_LOOKALIKES = 'АВСЕНКМРТХУ';
const LATIN_TWINS = 'ABCEHKMPTXY';

export function normalizePairingCode(input: string): string {
  return input
    .trim()
    .toUpperCase()
    .replace(/[\s-]/g, '')
    .replace(/[АВСЕНКМРТХУ]/g, (c) => LATIN_TWINS[CYRILLIC_LOOKALIKES.indexOf(c)]);
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

/**
 * The code on screen survives a reload; it is useless to anyone else once
 * redeemed or expired. It is kept with the account that made it: after a
 * sign-in to another account (or Forget this device) in the same browser,
 * a partner given that code would follow the earlier account.
 */
const PAIRING_KEY = 'nutricart.pairing';
export type StoredPairing = { owner: string; code: string; expiresAt: number };

export function readStoredPairing(): StoredPairing | null {
  try {
    const raw = localStorage.getItem(PAIRING_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredPairing>;
    return typeof parsed.owner === 'string' && typeof parsed.code === 'string' && (parsed.expiresAt ?? 0) > Date.now() ? (parsed as StoredPairing) : null;
  } catch {
    return null;
  }
}

export function writeStoredPairing(value: StoredPairing) {
  try {
    localStorage.setItem(PAIRING_KEY, JSON.stringify(value));
  } catch {
    /* private mode: the code just does not survive a reload */
  }
}

export function forgetStoredPairing() {
  try {
    localStorage.removeItem(PAIRING_KEY);
  } catch {
    /* private mode: nothing was stored */
  }
}
