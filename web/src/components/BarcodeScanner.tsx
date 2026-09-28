import { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { useI18n } from '../lib/i18n';

/**
 * Camera barcode reading in the browser (ZXing). The phone uses the Play
 * Services scanner that needs no camera permission; a browser has no such
 * thing, so this asks for the camera and stops it the moment a code is read
 * or the user closes the panel. Only retail formats are decoded, which makes
 * each frame cheaper and avoids false reads of QR codes on the pack.
 */
export function BarcodeScanner({ onCode, onClose }: { onCode: (code: string) => void; onClose: () => void }) {
  const { t } = useI18n();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('unsupported');
      return;
    }
    const hints = new Map();
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E]);
    hints.set(DecodeHintType.TRY_HARDER, true);
    const reader = new BrowserMultiFormatReader(hints);
    let stop: (() => void) | null = null;
    let done = false;
    reader
      .decodeFromConstraints({ video: { facingMode: { ideal: 'environment' } } }, videoRef.current!, (result, _err, controls) => {
        stop = () => controls.stop();
        if (result && !done) {
          done = true;
          controls.stop();
          onCode(result.getText());
        }
      })
      .catch((e: unknown) => setError(e instanceof Error ? `${e.name}: ${e.message}` : 'unknown'));
    return () => {
      done = true;
      stop?.();
    };
  }, [onCode]);

  return (
    <section className="card" aria-label={t('add.scan')}>
      {error === 'unsupported' ? (
        <p className="error">{t('add.scan_unsupported')}</p>
      ) : error ? (
        <p className="error">{t('add.scan_error', { error })}</p>
      ) : (
        <>
          <video ref={videoRef} style={{ width: '100%', borderRadius: 10, background: '#000' }} muted playsInline autoPlay />
          <p className="muted" style={{ fontSize: '0.85rem' }}>{t('add.scan_hint')}</p>
        </>
      )}
      <button className="ghost" onClick={onClose}>
        {t('add.scan_stop')}
      </button>
    </section>
  );
}
