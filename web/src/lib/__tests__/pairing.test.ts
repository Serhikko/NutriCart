import { describe, expect, it } from 'vitest';
import { isWellFormedPairingCode, normalizePairingCode } from '../pairing';

describe('pairing code input', () => {
  it('normalises like the phone and the server', () => {
    expect(normalizePairingCode(' 7kq-4md ')).toBe('7KQ4MD');
    expect(normalizePairingCode('7KQ 4MD')).toBe('7KQ4MD');
  });

  it('rejects wrong length and look-alike letters', () => {
    expect(isWellFormedPairingCode('7KQ4MD')).toBe(true);
    expect(isWellFormedPairingCode('7KQ4M')).toBe(false);
    expect(isWellFormedPairingCode('7KQ4MO')).toBe(false);
    expect(isWellFormedPairingCode('7KQ4M1')).toBe(false);
  });
});

describe('code generation and hashing', () => {
  it('generates well-formed codes from the shared alphabet', async () => {
    const { generatePairingCode, isWellFormedPairingCode } = await import('../pairing');
    for (let i = 0; i < 50; i += 1) expect(isWellFormedPairingCode(generatePairingCode())).toBe(true);
  });
  it("hashes like the server's digest(upper(trim(code)), 'sha256')", async () => {
    const { hashPairingCode } = await import('../pairing');
    const expected = 'e9c0f8b575cbfcb42ab3b78ecc87efa3b011d9a5d10b09fa4e96f240bf6a82f5';
    expect(await hashPairingCode('ABCDEF')).toBe(expected);
    expect(await hashPairingCode(' abc def ')).toBe(expected);
  });
});
