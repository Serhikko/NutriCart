import { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader } from '@zxing/browser';
import { useI18n } from '../lib/i18n';

/**
 * Camera barcode reading in the browser (ZXing). The phone uses the Play
 * Services scanner that needs no camera permission; a browser has no such
 * thing, so this asks for the camera and stops it the moment a code is read
 * or the user closes the panel.
 */
export function BarcodeScanner({ onCode, onClose }: { onCode: (code: string) => void; onClose: () => void }) {
  const { t } = useI18n();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [unsupported, setUnsupported] = useState(false);

  useEffect(() => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setUnsupported(true);
      return;
    }
    const reader = new BrowserMultiFormatReader();
    let stop: (() => void) | null = null;
    let done = false;
    reader
      .decodeFromConstraints({ video: { facingMode: 'environment' } }, videoRef.current!, (result, _err, controls) => {
        stop = () => controls.stop();
        if (result && !done) {
          done = true;
          controls.stop();
          onCode(result.getText());
        }
      })
      .catch(() => setUnsupported(true));
    return () => {
      done = true;
      stop?.();
    };
  }, [onCode]);

  return (
    <section className="card" aria-label={t('add.scan')}>
      {unsupported ? (
        <p className="error">{t('add.scan_unsupported')}</p>
      ) : (
        <>
          <video ref={videoRef} style={{ width: '100%', borderRadius: 10, background: '#000' }} muted playsInline />
          <p className="muted" style={{ fontSize: '0.85rem' }}>{t('add.scan_hint')}</p>
        </>
      )}
      <button className="ghost" onClick={onClose}>
        {t('add.scan_stop')}
      </button>
    </section>
  );
}
