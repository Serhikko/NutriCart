import { describe, expect, it } from 'vitest';
import { PASSWORD_ALPHABET, PASSWORD_LENGTH, generatePassword, isAcceptablePassword } from '../password';

describe('password generation', () => {
  it('has the length and alphabet, and differs each time', () => {
    const a = generatePassword();
    expect(a).toHaveLength(PASSWORD_LENGTH);
    expect([...a].every((c) => PASSWORD_ALPHABET.includes(c))).toBe(true);
    expect(a).not.toBe(generatePassword());
    expect(isAcceptablePassword(a)).toBe(true);
  });
  it('refuses short or blank passwords', () => {
    expect(isAcceptablePassword('abc')).toBe(false);
    expect(isAcceptablePassword('        ')).toBe(false);
    expect(isAcceptablePassword('correct horse')).toBe(true);
  });
});
