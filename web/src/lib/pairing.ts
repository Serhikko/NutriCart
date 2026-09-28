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
