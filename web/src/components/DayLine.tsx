import { useI18n } from '../lib/i18n';
import { dayLine, type DaySummary } from '../lib/diary';

/** "1230 / 2100 kcal, 870 left": the phone's number, never recomputed here. */
export function DayLine({ summary, eatenFallback }: { summary: DaySummary | null; eatenFallback: number }) {
  const { t } = useI18n();
  const line = dayLine(summary, eatenFallback);
  let text: string;
  if (line.target === null) text = t('day.line_no_target', { eaten: line.eaten });
  else if (line.remaining! >= 0) text = t('day.line_target', { eaten: line.eaten, target: line.target, remaining: line.remaining! });
  else text = t('day.line_over', { eaten: line.eaten, target: line.target, over: -line.remaining! });
  return <div className="stat"><span className="big">{text}</span></div>;
}
