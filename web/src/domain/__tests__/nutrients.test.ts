import { describe, expect, it } from 'vitest';
import { fiberTargetG, limitState, saturatedFatLimitG, sugarLimitG, targetState } from '../nutrients';

describe('NutrientTargets port', () => {
  it('2000 kcal gives the textbook guides', () => {
    expect(fiberTargetG(2000)).toBe(28);
    expect(sugarLimitG(2000)).toBe(50);
    expect(saturatedFatLimitG(2000)).toBe(22);
  });
  it('guides scale with the kcal target', () => {
    expect(fiberTargetG(1500)).toBe(21);
    expect(sugarLimitG(1500)).toBe(38);
    expect(saturatedFatLimitG(1500)).toBe(17);
  });
  it('target nutrients: neutral, good band, over, no verdict at zero', () => {
    expect(targetState(25, 28)).toBe('NEUTRAL');
    expect(targetState(25.2, 28)).toBe('GOOD');
    expect(targetState(28, 28)).toBe('GOOD');
    expect(targetState(30.8, 28)).toBe('GOOD');
    expect(targetState(31, 28)).toBe('OVER');
    expect(targetState(10, 0)).toBe('NEUTRAL');
  });
  it('limit nutrients: good, warn to 120%, over, no verdict at zero', () => {
    expect(limitState(0, 5)).toBe('GOOD');
    expect(limitState(5, 5)).toBe('GOOD');
    expect(limitState(5.5, 5)).toBe('WARN');
    expect(limitState(6, 5)).toBe('WARN');
    expect(limitState(6.1, 5)).toBe('OVER');
    expect(limitState(3, 0)).toBe('NEUTRAL');
  });
});
