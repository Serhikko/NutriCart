import { createContext, useContext, useEffect, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from './Icon';
import { setNavDirection } from '../../lib/motion';

type SetCompactTitle = (text: string | null) => void;

/**
 * The single compact top bar lives in Layout (outside the receding stage);
 * the page's LargeTitle tells it what to say through this context. Outside
 * Layout (tests) it is a no-op.
 */
export const CompactTitleContext = createContext<SetCompactTitle>(() => undefined);

/**
 * Puts `text` in the compact top bar that fades in once the large title has
 * scrolled away ("My day · 870 left": the part after " · " is drawn
 * secondary). Cleared when the page unmounts.
 */
export function useCompactTitle(text: string | null | undefined): void {
  const setCompact = useContext(CompactTitleContext);
  useEffect(() => {
    setCompact(text ?? null);
    return () => setCompact(null);
  }, [setCompact, text]);
}

/** Entrance delay (ms) for the .a-* classes, as a per-element custom property. */
const delay = (ms: number) => ({ '--d': ms }) as CSSProperties;

interface LargeTitleProps {
  /** Sentence-case line above the title, in the tint (the long date, "Dinner · Friday 9 October"). */
  eyebrow?: ReactNode;
  /** The page's one h1. */
  title: ReactNode;
  /** A "‹ My day" link above everything, for drill-in pages. */
  back?: { to: string; label: string };
  /** Controls on the title's row (DayNav, a streak chip, the desktop "Add food"). */
  actions?: ReactNode;
  /** What the compact top bar says on scroll; defaults to the title when it is plain text. */
  compact?: string | null;
  className?: string;
}

/**
 * The page header: eyebrow, large title (rising out of its mask on a first
 * open), and the actions row. Renders the page's h1.
 */
export function LargeTitle({ eyebrow, title, back, actions, compact, className }: LargeTitleProps) {
  useCompactTitle(compact !== undefined ? compact : typeof title === 'string' ? title : null);
  return (
    <header className={['lh', className ?? ''].filter(Boolean).join(' ')}>
      {back && (
        <Link className="back a-fade-up" to={back.to} viewTransition onClick={() => setNavDirection('back')}>
          <Icon name="left" />
          {back.label}
        </Link>
      )}
      {eyebrow != null && eyebrow !== false && (
        <p className="eyebrow a-fade-up" style={delay(back ? 20 : 0)}>
          {eyebrow}
        </p>
      )}
      <div className="lh-row">
        <h1 className="large-title title-clip a-title" style={delay(back ? 40 : 30)}>
          <span>{title}</span>
        </h1>
        {actions != null && actions !== false && (
          <div className="lh-actions a-fade-up" style={delay(90)}>
            {actions}
          </div>
        )}
      </div>
    </header>
  );
}
