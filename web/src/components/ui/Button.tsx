import type { ComponentProps } from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { Icon, type IconName } from './Icon';

export type ButtonVariant = 'ink' | 'fill' | 'plain' | 'water' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonLook {
  /** ink: the primary (near-black, white at night); fill: secondary; plain: tinted text;
   *  water: the water card's +250 / +500; danger: destructive, always with a word. */
  variant?: ButtonVariant;
  /** sm 36 px (hit area 44), md 44 px, lg 54 px full width. */
  size?: ButtonSize;
  /** A leading glyph, drawn at 20 px. */
  icon?: IconName;
}

function buttonClass(variant: ButtonVariant, size: ButtonSize, className?: string): string {
  return ['btn', `btn-${variant}`, size === 'md' ? '' : `btn-${size}`, className ?? ''].filter(Boolean).join(' ');
}

/**
 * A capsule button. It keeps the native `type` default (submit inside a
 * form), so a search or barcode form still submits on Enter; pass
 * type="button" for buttons inside a form that must not submit.
 */
export function Button({ variant = 'ink', size = 'md', icon, className, children, ...rest }: ComponentProps<'button'> & ButtonLook) {
  return (
    <button className={buttonClass(variant, size, className)} {...rest}>
      {icon && <Icon name={icon} size="sm" />}
      {children}
    </button>
  );
}

/** A router link drawn as a button ("Open my day", the desktop "Add food"). */
export function ButtonLink({ variant = 'ink', size = 'md', icon, className, children, ...rest }: LinkProps & ButtonLook) {
  return (
    <Link className={buttonClass(variant, size, className)} {...rest}>
      {icon && <Icon name={icon} size="sm" />}
      {children}
    </Link>
  );
}

/** A round 44 px button with a glyph only; `label` is its accessible name. */
export function IconButton({ icon, label, className, type = 'button', ...rest }: Omit<ComponentProps<'button'>, 'children'> & { icon: IconName; label: string }) {
  return (
    <button type={type} className={['icon-btn', className ?? ''].filter(Boolean).join(' ')} aria-label={label} {...rest}>
      <Icon name={icon} />
    </button>
  );
}
