import { useState, type CSSProperties, type ReactNode } from 'react';
import { useI18n } from '../lib/i18n';
import { dayLine, type DaySummary } from '../lib/diary';
import { dayLineText, inZone } from '../lib/dayView';
import { Ring } from './ui/Ring';
import { Digits } from './ui/Digits';
import { Icon } from './ui/Icon';

/** Entrance delay (ms) for the .a-* classes, as a per-element custom property. */
const d = (ms: number) => ({ '--d': ms }) as CSSProperties;

interface DayHeroProps {
  /** The day's target and eaten total (the phone's published row, or the site's own for a web account). */
  summary: DaySummary | null;
  /** Eaten kcal when there is no summary. */
  eatenFallback: number;
  /** The day shown: a different day is a day change (the ring moves), never the zone moment. */
  epochDay: number;
  /** The first open of the day: the ring sweeps from zero, the digits arrive, the halo blooms, light runs round. */
  first?: boolean;
  /**
   * The eaten kcal the hero showed before it mounted (back from Add food): the
   * ring re-sweeps from it and the numbers hand off to the new ones, once.
   */
  from?: number | null;
  /** The hero ring is the shared element of the page transition from the amount sheet (A1). */
  shared?: boolean;
  /** Beside the ring on a desktop, under the KPIs on a phone: the macros card, or a partner's tiles. */
  children?: ReactNode;
  className?: string;
}

/**
 * The day at a glance: one Ember ring of eaten against the target, the kcal
 * left (or over) in large rounded numerals inside it, the "In the zone" badge
 * between 90 % and 105 %, and the Eaten and Goal figures under it. Without a
 * target (a partner who has not published one) there is no ring: the eaten
 * kcal is the numeral.
 *
 * The ring and its centre are one picture named by the day line ("1,230 /
 * 2,100 kcal, 870 left"), the sentence the page always had, and "In the zone"
 * after it while the badge shows.
 *
 * Motion (DESIGN.md §5.2): the first open builds it (T1-T6); back from Add the
 * ring re-sweeps from the old value and the numbers hand off (A2, A3); any
 * later change moves from the value on screen; landing in the zone (on the
 * first open, or a change that crosses 90 %) pulses the halo twice, runs one
 * pass of light and pops the badge (Z1-Z3).
 */
export function DayHero({ summary, eatenFallback, epochDay, first = false, from = null, shared = false, children, className }: DayHeroProps) {
  const { t, tag } = useI18n();
  const nf = new Intl.NumberFormat(tag, { maximumFractionDigits: 0 });
  const line = dayLine(summary, eatenFallback);
  const { eaten, target } = line;
  const hasTarget = target !== null && target > 0;
  const zone = hasTarget && inZone(eaten, target);
  // How the hero entered is decided once, when it appears; later prop changes never replay it.
  const [intro] = useState(() => ({ first, from: hasTarget ? from : null }));
  const arriving = intro.from != null;
  const before = intro.from ?? 0;
  // The first-open build (T1-T6). Back from Add the hero is already there: the shared ring lands
  // on it (A1) and the numbers hand off, so its own entrances (the wrap turning in, the halo
  // blooming, the caption and KPIs rising) stay still, even on the page's first open of the day.
  const build = intro.first && !arriving;

  // The zone moment plays when the ring lands in the zone: on the first open, back from Add
  // when the add crossed 90 %, or when a later change (a live insert, an undo) crosses it.
  // Each moment re-keys the glow and the badge, so their one-shot animations play again.
  const [moment, setMoment] = useState(() => ({
    n: zone && ((intro.first && !arriving) || (arriving && !inZone(before, target))) ? 1 : 0,
    eaten,
    epochDay,
  }));
  let current = moment;
  if (moment.eaten !== eaten || moment.epochDay !== epochDay) {
    const crossed = zone && moment.epochDay === epochDay && !inZone(moment.eaten, target);
    current = { n: crossed ? moment.n + 1 : moment.n, eaten, epochDay };
    setMoment(current);
  }
  const playing = current.n > 0;

  const remaining = hasTarget ? target - eaten : 0;
  const over = remaining < 0;
  const numeral = hasTarget ? Math.abs(remaining) : eaten;
  const numeralFrom = arriving && hasTarget ? Math.abs(target - before) : undefined;
  const long = nf.format(numeral).length >= 5;
  // The badge is drawn inside the picture, so its words join the picture's name.
  const spoken = dayLineText(t, tag, summary, eatenFallback);
  const label = zone ? `${spoken}, ${t('day.zone')}` : spoken;
  const kcal = t('unit.kcal');

  // One pass of light: at the end of the first build (700 ms), or after the sweep when the
  // ring lands in the zone (1250 ms; nudged by the moment's number so a second one replays).
  const shine = playing ? 1250 + (current.n - 1) : build ? 700 : null;

  return (
    <div
      className={['hero', hasTarget ? '' : 'no-target', zone ? 'in-zone' : '', playing ? 'zone-moment' : '', className ?? '']
        .filter(Boolean)
        .join(' ')}
    >
      <div className={build ? 'ring-wrap a-ring' : 'ring-wrap'} style={d(40)} role="img" aria-label={label}>
        <div className={build ? 'stage-light a-bloom' : 'stage-light'} style={d(180)} aria-hidden="true" />
        {zone && <div key={`glow-${current.n}`} className="stage-light zone-glow" style={d(1250)} aria-hidden="true" />}
        {hasTarget && (
          <Ring
            value={eaten}
            target={target}
            size={252}
            stroke={26}
            from={arriving ? before : undefined}
            sweep={intro.first || arriving}
            delay={arriving ? 160 : 140}
            shine={shine}
            className={shared ? 'hero-ring shared' : 'hero-ring'}
          />
        )}
        <div className="hero-center" aria-hidden="true">
          <div className={long ? 'hero-num long' : 'hero-num'}>
            <Digits value={numeral} enter={build} delay={arriving ? 240 : 200} step={60} from={numeralFrom} />
          </div>
          <div className={build ? 'hero-cap a-fade-up' : 'hero-cap'} style={d(330)}>
            {!hasTarget ? kcal : over ? t('day.over_caption') : t('day.left')}
          </div>
          {zone && (
            <div key={`badge-${current.n}`} className="zone-badge" style={d(1150)}>
              <Icon name="check" size="xs" />
              {t('day.zone')}
            </div>
          )}
        </div>
      </div>
      <div className="hero-side">
        {hasTarget && (
          <div className={build ? 'kpis a-fade-up' : 'kpis'} style={d(260)}>
            <div className="kpi">
              <span className="k-l">{t('day.eaten')}</span>
              <span className="k-v num">
                <Digits value={eaten} from={arriving ? before : undefined} delay={300} />
                <span className="unit">{kcal}</span>
              </span>
            </div>
            <div className="kpi">
              <span className="k-l">{t('day.goal')}</span>
              <span className="k-v num">
                <Digits value={target} />
                <span className="unit">{kcal}</span>
              </span>
            </div>
          </div>
        )}
        {children}
      </div>
    </div>
  );
}
