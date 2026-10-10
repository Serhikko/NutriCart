import { useSyncExternalStore } from 'react';
import { useI18n } from '../../lib/i18n';
import { Icon } from './Icon';

function subscribe(onChange: () => void): () => void {
  window.addEventListener('online', onChange);
  window.addEventListener('offline', onChange);
  return () => {
    window.removeEventListener('online', onChange);
    window.removeEventListener('offline', onChange);
  };
}

const isOnline = () => (typeof navigator === 'undefined' ? true : navigator.onLine !== false);

/** navigator.onLine, live. */
export function useOnline(): boolean {
  return useSyncExternalStore(subscribe, isOnline, () => true);
}

/**
 * A quiet strip under the large title while the browser is offline: the
 * page keeps showing what was already loaded, and this says so. It renders
 * nothing while online, and is announced politely when it appears.
 */
export function OfflineBanner({ className }: { className?: string }) {
  const { t } = useI18n();
  const online = useOnline();
  if (online) return null;
  return (
    <div className={['offline', className ?? ''].filter(Boolean).join(' ')} role="status">
      <Icon name="offline" size="sm" />
      <span>{t('offline.banner')}</span>
    </div>
  );
}
