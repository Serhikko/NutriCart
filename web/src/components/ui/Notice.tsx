import type { AriaRole, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

export type NoticeTone = 'info' | 'warning' | 'error';

interface NoticeProps {
  tone?: NoticeTone;
  /** The sentence that matters ("Could not reach Open Food Facts."), in its own element. */
  title: ReactNode;
  /** The supporting line (the server's detail), in a separate element under the title. */
  detail?: ReactNode;
  /** A control under the text, e.g. <Button variant="fill" size="sm">Try again</Button>. */
  action?: ReactNode;
  /** Another glyph than the tone's (e.g. 'offline'). */
  icon?: IconName;
  /** Errors are announced (role="alert") by default; pass a role, or null for none. */
  role?: AriaRole | null;
  className?: string;
  id?: string;
}

const TONE_ICON: Record<NoticeTone, IconName> = { info: 'info', warning: 'warning', error: 'warning' };

/**
 * An inline message card: a tone glyph, a title, an optional detail and an
 * optional action. Errors draw the glyph and title in the danger colour and
 * always say what happened in words. On the stage it is a card; inside a card
 * or a sheet it becomes a nested grey block.
 */
export function Notice({ tone = 'info', title, detail, action, icon, role, className, id }: NoticeProps) {
  const resolvedRole = role === undefined ? (tone === 'error' ? 'alert' : undefined) : (role ?? undefined);
  return (
    <div id={id} className={['notice', `tone-${tone}`, className ?? ''].filter(Boolean).join(' ')} role={resolvedRole}>
      <Icon name={icon ?? TONE_ICON[tone]} size="sm" />
      <div className="notice-text">
        <p className="notice-title">{title}</p>
        {detail != null && detail !== '' && <p className="notice-detail">{detail}</p>}
        {action != null && <div className="notice-action">{action}</div>}
      </div>
    </div>
  );
}
