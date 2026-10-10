import { ICONS, type IconName } from './icons';

export type { IconName } from './icons';
export type IconSize = 'xs' | 'sm' | 'md' | 'lg';

interface IconProps {
  name: IconName;
  /** xs 16, sm 20, md 24 (default), lg 28; the stroke thickens as the glyph shrinks. */
  size?: IconSize;
  className?: string;
}

/**
 * One glyph from the icon set. Always decorative: the control around it
 * carries the accessible name (a visible label or an aria-label).
 */
export function Icon({ name, size = 'md', className }: IconProps) {
  const cls = ['icon', size === 'md' ? '' : size, className ?? ''].filter(Boolean).join(' ');
  return (
    <svg className={cls} viewBox="0 0 24 24" aria-hidden="true" focusable="false" dangerouslySetInnerHTML={{ __html: ICONS[name] }} />
  );
}
