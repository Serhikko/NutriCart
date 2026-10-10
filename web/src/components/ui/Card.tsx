import type { HTMLAttributes, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** The data accents a card can take (.m-* in base.css): its label and glyph use the metric's ink. */
export type Metric = 'kcal' | 'protein' | 'fat' | 'carbs' | 'water' | 'weight' | 'steps';

type CardProps = HTMLAttributes<HTMLElement> & {
  /** The element to render: section by default, article for tiles. (A whole-card link is a Link with className="card".) */
  as?: 'section' | 'article' | 'div' | 'aside';
  metric?: Metric;
  /** The id of the element that names the card (usually CardHead's id). */
  labelledBy?: string;
};

/** A solid white (graphite at night) card on the stage: radius 24, padding 18 (20 on desktop). */
export function Card({ as: Tag = 'section', metric, labelledBy, className, children, ...rest }: CardProps) {
  const cls = ['card', metric ? `m-${metric}` : '', className ?? ''].filter(Boolean).join(' ');
  return (
    <Tag className={cls} aria-labelledby={labelledBy} {...rest}>
      {children}
    </Tag>
  );
}

interface CardHeadProps {
  /** A 19 px glyph before the label. */
  icon?: IconName;
  label: ReactNode;
  /** Paints the label and glyph in this metric's ink (otherwise the card's metric, if any). */
  metric?: Metric;
  /** Right-aligned footnote ("Last at 15:30", "Last 7 days"). */
  meta?: ReactNode;
  /** A trailing control, usually a plain button or link ("Targets", "Edit"). */
  action?: ReactNode;
  /** Put on the label, for Card's labelledBy. */
  id?: string;
  /** The label's element: a heading by default, so cards are reachable by heading. */
  as?: 'h2' | 'h3' | 'span';
}

/** The Health-style card header: sentence-case label (with glyph) left, meta or an action right. */
export function CardHead({ icon, label, metric, meta, action, id, as: Label = 'h2' }: CardHeadProps) {
  const inked = Boolean(metric || icon);
  const cls = ['label', metric ? `m-${metric}` : '', inked ? 'ink' : ''].filter(Boolean).join(' ');
  return (
    <div className="card-head">
      <Label className={cls} id={id}>
        {icon && <Icon name={icon} />}
        {label}
      </Label>
      {meta != null && meta !== false && <span className="meta">{meta}</span>}
      {action}
    </div>
  );
}
