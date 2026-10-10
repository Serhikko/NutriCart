import { afterEach, describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import { firstOpenKey, spring, useFirstOpen, usePrevious, useReducedMotion, canAnimate } from '../motion';

afterEach(() => sessionStorage.clear());

describe('motion helpers without browser motion APIs (jsdom)', () => {
  it('reports no reduced motion and no Web Animations', () => {
    const { result } = renderHook(() => useReducedMotion());
    expect(result.current).toBe(false);
    expect(typeof canAnimate()).toBe('boolean');
  });

  it('falls back to the cubic-bezier springs when the tokens are not loaded', () => {
    expect(spring('snappy')).toBe('cubic-bezier(.2, 1.08, .36, 1)');
  });
});

describe('useFirstOpen', () => {
  it('is true for the whole first visit of a key today, false on the next', () => {
    const first = renderHook(() => useFirstOpen('day'));
    expect(first.result.current).toBe(true);
    first.rerender();
    expect(first.result.current).toBe(true);
    first.unmount();
    const again = renderHook(() => useFirstOpen('day'));
    expect(again.result.current).toBe(false);
  });

  it('judges a new key when it changes under a mounted component', () => {
    sessionStorage.clear();
    const { result, rerender } = renderHook(({ key }) => useFirstOpen(key), { initialProps: { key: 'week' } });
    expect(result.current).toBe(true);
    rerender({ key: 'week' });
    rerender({ key: 'settings' });
    expect(result.current).toBe(true);
    rerender({ key: 'week' });
    expect(result.current).toBe(false);
  });
});

describe('usePrevious', () => {
  it('returns the value of the previous render', () => {
    const { result, rerender } = renderHook(({ v }) => usePrevious(v), { initialProps: { v: 870 } });
    expect(result.current).toBeUndefined();
    rerender({ v: 709 });
    expect(result.current).toBe(870);
    rerender({ v: 709 });
    expect(result.current).toBe(709);
  });
});

describe('firstOpenKey', () => {
  it('names each route', () => {
    expect(firstOpenKey('/')).toBe('home');
    expect(firstOpenKey('/me/day')).toBe('day');
    expect(firstOpenKey('/me/add/20735/LUNCH')).toBe('add');
    expect(firstOpenKey('/a/8e2d/week')).toBe('partner-week');
  });
});
