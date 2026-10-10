import { useId, useRef, useState, type CSSProperties, type FormEvent } from 'react';
import { useI18n } from '../lib/i18n';
import { useMyNudges, useMyProfile, useSendNudge } from '../lib/queries';
import { sentAt } from '../lib/dayView';
import { Card, CardHead } from './ui/Card';
import { Icon } from './ui/Icon';
import { useToast } from './ui/Toast';

/**
 * The one thing a partner can send back: a short message that becomes a
 * notification on the phone. A capsule field with a round send button, three
 * quick messages that fill the field, the outcome in a polite live region
 * (and a toast when it went out), and your recent nudges as right-aligned
 * bubbles ("21:05 · seen", with the date first when it was not today). The
 * card's id is "nudge": the bell in the tab bar brings it up.
 */
export function NudgeBox({ ownerId, userId }: { ownerId: string; userId: string | null }) {
  const { t, tag } = useI18n();
  const titleId = useId();
  const myName = useMyProfile(userId);
  const nudges = useMyNudges(ownerId, userId);
  const send = useSendNudge(ownerId, userId, myName.data ?? '');
  const toast = useToast();
  const [text, setText] = useState('');
  const field = useRef<HTMLInputElement>(null);

  // Nudges that join the list after it first loaded unfold into place (P2).
  const seen = useRef<Set<string> | null>(null);
  const fresh = useRef(new Set<string>());
  if (nudges.data) {
    if (!seen.current) seen.current = new Set(nudges.data.map((n) => n.id));
    else
      for (const n of nudges.data)
        if (!seen.current.has(n.id)) {
          seen.current.add(n.id);
          fresh.current.add(n.id);
        }
  }

  const onSend = (event?: FormEvent) => {
    event?.preventDefault();
    if (send.isPending || !userId) return;
    const body = text.trim() || t('nudge.placeholder');
    send.mutate(body, {
      onSuccess: () => {
        setText('');
        // P2: a one-line toast confirms it (a toast does not wrap); the status line under the
        // chips keeps the whole sentence, with when it shows on the phone.
        toast({ text: t('nudge.sent_toast') });
      },
    });
  };

  const quick = [t('nudge.placeholder'), t('nudge.quick_water'), t('nudge.quick_proud')];

  return (
    <Card id="nudge" className="nudge-card a-rise" style={{ '--d': 600 } as CSSProperties} labelledBy={titleId}>
      <CardHead id={titleId} icon="bell" label={t('nudge.title')} metric="kcal" />
      <form className="nudge-form" onSubmit={onSend}>
        <label className="nudge-field">
          <input
            ref={field}
            type="text"
            value={text}
            placeholder={t('nudge.placeholder')}
            aria-label={t('nudge.title')}
            maxLength={500}
            enterKeyHint="send"
            onChange={(e) => setText(e.target.value)}
          />
          <button type="submit" className="send" aria-label={t('nudge.send')} disabled={send.isPending || !userId}>
            <Icon name="send" size="sm" />
          </button>
        </label>
      </form>
      <div className="chips nudge-chips">
        {quick.map((q) => (
          <button
            key={q}
            type="button"
            className="chip"
            onClick={() => {
              setText(q);
              field.current?.focus({ preventScroll: true });
            }}
          >
            {q}
          </button>
        ))}
      </div>
      <p className={send.isError ? 'nudge-status danger-text' : 'nudge-status'} aria-live="polite">
        {send.isSuccess ? t('nudge.sent') : send.isError ? t('nudge.failed') : ''}
      </p>
      {nudges.data && nudges.data.length > 0 && (
        <div className="nudge-recent">
          <h3 className="nudge-recent-t">{t('nudge.recent')}</h3>
          <ul className="bubbles">
            {nudges.data.map((n) => (
              <li key={n.id} className={fresh.current.has(n.id) ? 'bubble-wrap fresh a-unfold' : 'bubble-wrap'}>
                <div className="bubble-clip">
                  <div className="bubble">
                    <p>{n.text}</p>
                    <span className="stamp num">
                      <span>{sentAt(n.created_at, tag)}</span> · <span>{n.seen_at ? t('nudge.seen') : t('nudge.pending')}</span>
                    </span>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
