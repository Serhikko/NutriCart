import { useEffect, useRef, useState } from 'react';
import { BrowserMultiFormatReader, type IScannerControls } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import { useI18n } from '../lib/i18n';
import { prefersReducedMotion } from '../lib/motion';
import { Icon } from './ui/Icon';
import { Button } from './ui/Button';
import { Notice } from './ui/Notice';

// The brackets snap shut and the frame flashes once before the sheet makes way for the result (S13).
const READ_PAUSE_MS = 260;

/**
 * Camera barcode reading in the browser (ZXing). The phone uses the Play
 * Services scanner that needs no camera permission; a browser has no such
 * thing, so this asks for the camera and stops it the moment a code is read
 * or the user closes the panel. Only retail formats are decoded, which makes
 * each frame cheaper and avoids false reads of QR codes on the pack.
 *
 * Drawn as the content of a Sheet: a 4:3 viewfinder with four corner
 * brackets that breathe twice while the camera starts, the hint, a torch
 * button when the camera has one, and Stop. A camera that cannot start says
 * why; the barcode field on the page stays there to type the code instead.
 */
export function BarcodeScanner({ onCode, onClose }: { onCode: (code: string) => void; onClose: () => void }) {
  const { t } = useI18n();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState(false);
  const [read, setRead] = useState(false);
  const [torch, setTorch] = useState<{ switch: (on: boolean) => Promise<void>; on: boolean } | null>(null);

  useEffect(() => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('unsupported');
      return;
    }
    const hints = new Map();
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E]);
    hints.set(DecodeHintType.TRY_HARDER, true);
    const reader = new BrowserMultiFormatReader(hints);
    let controls: IScannerControls | null = null;
    let done = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    reader
      .decodeFromConstraints({ video: { facingMode: { ideal: 'environment' } } }, videoRef.current!, (result, _err, c) => {
        controls = c;
        if (result && !done) {
          done = true;
          c.stop();
          const code = result.getText();
          setRead(true);
          if (prefersReducedMotion()) onCode(code);
          else timer = setTimeout(() => onCode(code), READ_PAUSE_MS);
        }
      })
      .then((c) => {
        controls = c;
        // Closed while the camera was still starting: let it go at once.
        if (done) {
          c.stop();
          return;
        }
        setLive(true);
        // ZXing offers the torch only when the camera track has one.
        if (c.switchTorch) setTorch({ switch: c.switchTorch, on: false });
      })
      .catch((e: unknown) => setError(e instanceof Error ? `${e.name}: ${e.message}` : 'unknown'));
    return () => {
      done = true;
      clearTimeout(timer);
      controls?.stop();
    };
  }, [onCode]);

  const toggleTorch = () => {
    if (!torch) return;
    const next = !torch.on;
    torch.switch(next).then(
      () => setTorch((s) => (s ? { ...s, on: next } : s)),
      () => setTorch(null),
    );
  };

  const frameClass = ['viewfinder', live ? 'is-live' : '', read ? 'is-read' : ''].filter(Boolean).join(' ');

  return (
    <div className="scanner">
      <div className="scanner-head">
        <h2>{t('add.scan')}</h2>
      </div>
      {error ? (
        <Notice
          tone="error"
          icon={error === 'unsupported' ? 'camera' : 'warning'}
          title={error === 'unsupported' ? t('add.scan_unsupported') : t('add.scan_error', { error })}
        />
      ) : (
        <div className="scanner-main">
          <div className={frameClass}>
            <video ref={videoRef} muted playsInline autoPlay />
            <span className="vf-corners" aria-hidden="true">
              <i />
              <i />
              <i />
              <i />
            </span>
            <span className="vf-flash" aria-hidden="true" />
          </div>
          <p className="scanner-hint">{t('add.scan_hint')}</p>
        </div>
      )}
      <div className="scanner-foot">
        {torch && (
          <button
            type="button"
            className="icon-btn torch"
            aria-label={t('add.torch')}
            aria-pressed={torch.on}
            onClick={toggleTorch}
          >
            <Icon name="torch" />
          </button>
        )}
        <Button type="button" variant="fill" size="lg" onClick={onClose}>
          {t('add.scan_stop')}
        </Button>
      </div>
    </div>
  );
}
