import type { CSSProperties } from 'react';
import { useI18n } from '../lib/i18n';
import { PAIRING_CODE_LENGTH, PAIRING_VALIDITY_MINUTES } from '../lib/pairing';
import { Button, IconButton } from './ui/Button';
import { Notice } from './ui/Notice';
import { Ring } from './ui/Ring';

interface PairingCodeCardProps {
  /** The live code, or null when none has been made (or the last one is gone). */
  code: string | null;
  /** Whole minutes the code still works; 0 once it has expired. */
  minutesLeft: number;
  /** A code was made and has run out ("The last code has expired."). */
  expired: boolean;
  /** Made during this visit: its characters arrive one by one. */
  fresh?: boolean;
  onNew: () => void;
  pending?: boolean;
  /** Making a code failed. */
  failed?: boolean;
  /** Copies the code; omitted where the clipboard is not available. */
  onCopy?: () => void;
  copyLabel?: string;
}

/**
 * The code a partner types on Welcome to follow this account (Settings →
 * Share your day): the six characters large in a grey capsule, a small ring
 * that empties as the 15 minutes run out (N1), the hint, and "New code".
 * Without a live code the capsule shows six empty places instead, so the
 * group keeps its shape.
 */
export function PairingCodeCard({ code, minutesLeft, expired, fresh = false, onNew, pending = false, failed = false, onCopy, copyLabel }: PairingCodeCardProps) {
  const { t } = useI18n();
  const live = code !== null && minutesLeft > 0;

  return (
    <div className="code-card">
      <div className={['code-pill', live ? '' : 'is-empty', live && onCopy && copyLabel ? 'has-copy' : ''].filter(Boolean).join(' ')}>
        {live ? (
          // The code is one word for screen readers, tests and the page's text (like Digits); the
          // characters drawn one by one are decoration. Keyed by the code, so a new one arrives
          // character by character.
          <span className={fresh ? 'code-text is-fresh' : 'code-text'} key={code}>
            <span className="sr">{code}</span>
            <span className="code-chars" aria-hidden="true">
              {[...code].map((ch, i) => (
                <span key={i} style={{ '--i': i } as CSSProperties}>
                  {ch}
                </span>
              ))}
            </span>
          </span>
        ) : (
          <span className="code-text" aria-hidden="true">
            {Array.from({ length: PAIRING_CODE_LENGTH }, (_, i) => (
              <span key={i} className="code-dot" />
            ))}
          </span>
        )}
        {live && onCopy && copyLabel && <IconButton icon="copy" label={copyLabel} className="code-copy" onClick={onCopy} />}
      </div>

      {live ? (
        <div className="code-timer">
          <span className="code-clock" aria-hidden="true">
            <Ring value={minutesLeft} target={PAIRING_VALIDITY_MINUTES} size={44} stroke={5} className="tick" />
            <b className="num">{minutesLeft}</b>
          </span>
          <p className="footnote">{t('settings.code_hint', { min: minutesLeft })}</p>
        </div>
      ) : expired ? (
        <p className="footnote code-expired">{t('settings.code_expired')}</p>
      ) : null}

      {failed && <Notice tone="error" title={t('welcome.failed')} />}

      <Button variant="fill" icon="refresh" className="code-new" onClick={onNew} disabled={pending}>
        {t('settings.code_new')}
      </Button>
    </div>
  );
}
