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
