import type { CSSProperties } from 'react';
import { useI18n } from '../lib/i18n';
import type { FoodProduct } from '../domain/openFoodFacts';
import { Icon, type IconName } from './ui/Icon';

/** A product the user added under its barcode (custom_products), listed before Open Food Facts. */
export const isOwnProduct = (product: FoodProduct) => product.id.startsWith('local:');

/**
 * The glyph beside a product: a bottle for a drink, a leaf for one of the
 * user's own products, a neutral bowl for everything else. Never a letter
 * monogram, and never a guess at what the food is from its name.
 */
export function productGlyph(product: FoodProduct): IconName {
  if (isOwnProduct(product)) return 'leaf';
  return product.liquid ? 'bottle' : 'bowl';
}

interface ResultRowProps {
  product: FoodProduct;
  /** This product is the one in the amount sheet (the docked inspector shows which). */
  selected: boolean;
  onSelect: (product: FoodProduct) => void;
  /** Place in the list, for the entrance cascade (S1): 40 ms apart, at most ten steps. */
  index?: number;
}

/**
 * One search result: glyph, the name on up to two lines, the brand with the
 * portion size, and kcal per 100 g (or ml) at the end with a chevron. The
 * whole row is the button; the name never shrinks to one line, and the brand
 * and the kcal stay visible however long the name is.
 */
export function ResultRow({ product, selected, onSelect, index = 0 }: ResultRowProps) {
  const { t, tag } = useI18n();
  const unit = product.liquid ? t('unit.ml') : t('unit.g');
  const whole = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const mine = isOwnProduct(product);
  const portion = product.servingSizeG ? t('add.portion_size', { g: whole.format(product.servingSizeG), unit }) : null;
  const meta = [product.brand, portion].filter(Boolean).join(' · ');
  const delay = { '--d': 110 + Math.min(index, 10) * 40 } as CSSProperties;

  return (
    <button type="button" className="result" aria-pressed={selected} onClick={() => onSelect(product)} style={delay}>
      <span className="glyph" aria-hidden="true">
        <Icon name={productGlyph(product)} />
      </span>
      <span className="r-main">
        <span className="r-name">{product.name}</span>
        {(mine || meta) && (
          <span className="r-brand">
            {mine && <span className="mine">{t('add.mine')}</span>}
            {meta && <span className="r-meta">{meta}</span>}
          </span>
        )}
      </span>
      <span className="r-kcal">
        <b className="num">{whole.format(product.kcalPer100g)}</b>
        <span>
          {t('unit.kcal')}/100 {unit}
        </span>
      </span>
      <Icon name="right" className="chev" />
    </button>
  );
}
