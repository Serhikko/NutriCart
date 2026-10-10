/**
 * Port of FoodMath and NutritionLabelMath: labels state nutrition per 100 g,
 * the user eats some other amount, and a source may state energy in kJ or per
 * serving instead of kcal per 100 g.
 */

export interface Nutrition {
  kcal: number;
  proteinG: number;
  fatG: number;
  carbsG: number;
}

export function forGrams(kcalPer100g: number, proteinPer100g: number, fatPer100g: number, carbsPer100g: number, grams: number): Nutrition {
  const factor = grams / 100;
  return {
    kcal: kcalPer100g * factor,
    proteinG: proteinPer100g * factor,
    fatG: fatPer100g * factor,
    carbsG: carbsPer100g * factor,
  };
}

export const servingsToGrams = (servings: number, servingSizeG: number) => servings * servingSizeG;

/** Null passes through: "the label doesn't state fiber" must stay unknown. */
export function scalePer100g(valuePer100g: number | null, grams: number): number | null {
  return valuePer100g === null ? null : (valuePer100g * grams) / 100;
}

/** Thermochemical calorie, the factor EU/UK labels use. */
export const KJ_PER_KCAL = 4.184;
export const kcalFromKj = (kj: number) => kj / KJ_PER_KCAL;
export const kcalFromMacros = (proteinG: number, fatG: number, carbsG: number) => proteinG * 4 + fatG * 9 + carbsG * 4;

export function per100gFromServing(valuePerServing: number | null, servingSizeG: number | null): number | null {
  if (valuePerServing === null || servingSizeG === null || servingSizeG <= 0) return null;
  return (valuePerServing * 100) / servingSizeG;
}

/** kcal as stated, else kJ converted, else a per-serving value rescaled, else the macros. */
export function resolveKcalPer100g(v: {
  kcalPer100g: number | null;
  kjPer100g: number | null;
  kcalPerServing: number | null;
  kjPerServing: number | null;
  servingSizeG: number | null;
  proteinPer100g: number | null;
  fatPer100g: number | null;
  carbsPer100g: number | null;
}): number | null {
  if (v.kcalPer100g !== null) return v.kcalPer100g;
  if (v.kjPer100g !== null) return kcalFromKj(v.kjPer100g);
  const fromKcalServing = per100gFromServing(v.kcalPerServing, v.servingSizeG);
  if (fromKcalServing !== null) return fromKcalServing;
  const fromKjServing = per100gFromServing(v.kjPerServing, v.servingSizeG);
  if (fromKjServing !== null) return kcalFromKj(fromKjServing);
  if (v.proteinPer100g !== null && v.fatPer100g !== null && v.carbsPer100g !== null) {
    return kcalFromMacros(v.proteinPer100g, v.fatPer100g, v.carbsPer100g);
  }
  return null;
}
