import { useState } from 'react';
import { useI18n } from '../lib/i18n';
import { useMyNudges, useMyProfile, useSendNudge } from '../lib/queries';
import { formatTime } from '../lib/dates';

/** The one thing a partner can send back: a short message that becomes a notification on the phone. */
export function NudgeBox({ ownerId, userId }: { ownerId: string; userId: string | null }) {
  const { t, tag } = useI18n();
  const myName = useMyProfile(userId);
  const nudges = useMyNudges(ownerId, userId);
  const send = useSendNudge(ownerId, userId, myName.data ?? '');
  const [text, setText] = useState('');

  const onSend = () => {
    const body = text.trim() || t('nudge.placeholder');
    send.mutate(body, { onSuccess: () => setText('') });
  };

  return (
    <section className="card" aria-label={t('nudge.title')}>
      <h2 style={{ marginTop: 0 }}>{t('nudge.title')}</h2>
      <input
        type="text"
        value={text}
        placeholder={t('nudge.placeholder')}
        maxLength={500}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && onSend()}
      />
      <div className="row" style={{ marginTop: 10 }}>
        <span className="muted" style={{ fontSize: '0.85rem' }}>
          {send.isSuccess ? t('nudge.sent') : send.isError ? <span className="error">{t('nudge.failed')}</span> : ''}
        </span>
        <button onClick={onSend} disabled={send.isPending || !userId}>
          {t('nudge.send')}
        </button>
      </div>
      {nudges.data && nudges.data.length > 0 && (
        <>
          <h2>{t('nudge.recent')}</h2>
          <ul className="nudge-list">
            {nudges.data.map((n) => (
              <li key={n.id}>
                <span>{n.text}</span>
                <span className="pill">
                  {formatTime(n.created_at, tag)} · {n.seen_at ? t('nudge.seen') : t('nudge.pending')}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
