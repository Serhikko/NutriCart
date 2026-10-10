import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

interface EmptyStateProps {
  /** A 44 px glyph in the decoration grey above the title. */
  icon?: IconName;
  /** Decorative artwork instead of the glyph (Week's seven ghost capsules); hidden from screen readers. */
  art?: ReactNode;
  title: ReactNode;
  body?: ReactNode;
  /** Usually one ink button or link (<ButtonLink>); it sits under the text. */
  action?: ReactNode;
  /** The title's element: a heading where the empty state stands for a whole section. */
  titleAs?: 'h2' | 'h3' | 'p';
  className?: string;
}

/**
 * What a screen or a section shows when there is nothing in it yet: centred,
 * calm, with one way forward. The parts fade up one after another.
 */
export function EmptyState({ icon, art, title, body, action, titleAs: Title = 'h2', className }: EmptyStateProps) {
  return (
    <div className={['empty', className ?? ''].filter(Boolean).join(' ')}>
      {art != null ? (
        <div className="empty-art" aria-hidden="true">
          {art}
        </div>
      ) : (
        icon && (
          <span className="empty-icon">
            <Icon name={icon} size="lg" />
          </span>
        )
      )}
      <Title className="empty-title">{title}</Title>
      {body != null && body !== '' && <p className="empty-body">{body}</p>}
      {action != null && <div className="empty-action">{action}</div>}
    </div>
  );
}
