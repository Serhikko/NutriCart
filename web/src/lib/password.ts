/**
 * A password the site makes up for the account, so nobody has to invent one:
 * 14 symbols from an alphabet without look-alikes (no 0/O, 1/l/I), about
 * 80 bits, easy to read off a screen. Same shape as the phone's
 * PasswordGenerator.
 */
export const PASSWORD_LENGTH = 14;
export const PASSWORD_ALPHABET = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const PASSWORD_MIN_LENGTH = 8;

export function generatePassword(): string {
  const bytes = new Uint8Array(PASSWORD_LENGTH);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => PASSWORD_ALPHABET[b % PASSWORD_ALPHABET.length]).join('');
}

export function isAcceptablePassword(password: string): boolean {
  return password.trim().length > 0 && password.length >= PASSWORD_MIN_LENGTH;
}
