import { useLayoutEffect, useRef, useState } from 'react';

/**
 * The width a chart has to draw in, measured before the first paint and
 * again whenever its box changes (a ResizeObserver where there is one, the
 * window's resize event elsewhere). Measuring here, rather than through
 * recharts' ResponsiveContainer, means the chart is drawn at its real size in
 * the very first frame, so the entrance (bars rising, the goal wiping in)
 * plays on the final geometry instead of on an empty box. Where layout does
 * not exist (jsdom) it falls back to `fallback`, so the chart still renders.
 */
export function useChartWidth<T extends HTMLElement>(fallback = 320) {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const w = Math.floor(el.clientWidth);
      setWidth(w > 0 ? w : fallback);
    };
    measure();
    if (typeof ResizeObserver === 'function') {
      const ro = new ResizeObserver(measure);
      ro.observe(el);
      return () => ro.disconnect();
    }
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [fallback]);
  return [ref, width] as const;
}
