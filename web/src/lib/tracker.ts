import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { supabase } from './supabase';
import { fromEpochDay, todayEpochDay } from './dates';
import type { MealSlot } from './diary';
import type { FoodProduct } from '../domain/openFoodFacts';
import { forGrams, scalePer100g } from '../domain/food';
import { ageYears, dayTargetKcal, macroTargets, type TargetProfile } from '../domain/calories';
import type { ActivityLevel, Goal, Sex } from '../domain/model';
import { daySummaryPayload, type SummaryPolicy } from '../domain/summary';

/**
 * The website as a tracker for the signed-in user: their profile details,
 * their own diary writes, and the day summary the partner view reads. The
 * shapes and ids mirror what the phone writes, so a partner sees a web-only
 * account exactly like a phone account.
 */

export interface ProfileDetails {
  sex: Sex;
  birth_date: string; // YYYY-MM-DD
  height_cm: number;
  activity_level: ActivityLevel;
  goal: Goal;
  target_kg_per_week: number;
  custom_kcal_target: number | null;
  custom_protein_g: number | null;
  custom_fat_g: number | null;
  custom_carbs_g: number | null;
  primary_client: 'phone' | 'web';
}

export function useProfileDetails(userId: string | null) {
  return useQuery({
    queryKey: ['profileDetails', userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<ProfileDetails | null> => {
      const { data, error } = await supabase.from('profile_details').select('*').eq('user_id', userId!).maybeSingle();
      if (error) throw error;
      return (data as ProfileDetails | null) ?? null;
    },
  });
}

export function useSaveProfileDetails(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (details: ProfileDetails) => {
      const { error } = await supabase.from('profile_details').upsert({ user_id: userId, ...details });
      if (error) throw error;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['profileDetails', userId] });
      qc.invalidateQueries({ queryKey: ['day', userId] });
    },
  });
}

/** Latest weight, MANUAL beating HEALTH_CONNECT on the same day, like WeightDao.observeLatest. */
export function useLatestWeight(userId: string | null) {
  return useQuery({
    queryKey: ['latestWeight', userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<number | null> => {
      const { data, error } = await supabase
        .from('weight_entries')
        .select('epoch_day, source, weight_kg')
        .eq('owner_id', userId!)
        .order('epoch_day', { ascending: false })
        .limit(2);
      if (error) throw error;
      const rows = (data ?? []) as { epoch_day: number; source: string; weight_kg: number }[];
      if (rows.length === 0) return null;
      const top = rows.filter((r) => r.epoch_day === rows[0].epoch_day);
      return (top.find((r) => r.source === 'MANUAL') ?? top[0]).weight_kg;
    },
  });
}

/** The profile as the math wants it, for a given day; null until onboarding is done. */
export function targetProfileFor(details: ProfileDetails | null | undefined, weightKg: number | null | undefined, epochDay: number): TargetProfile | null {
  if (!details || weightKg == null) return null;
  const [y, m, d] = details.birth_date.split('-').map(Number);
  return {
    sex: details.sex,
    weightKg,
    heightCm: details.height_cm,
    ageYears: ageYears(new Date(y, m - 1, d), fromEpochDay(epochDay)),
    level: details.activity_level,
    goal: details.goal,
    targetKgPerWeek: details.target_kg_per_week,
    customKcalTarget: details.custom_kcal_target,
  };
}

export function targetsFor(profile: TargetProfile | null, details: ProfileDetails | null | undefined) {
  if (!profile) return null;
  const kcal = dayTargetKcal(profile, null, 0);
  if (details?.custom_kcal_target != null) {
    return {
      kcal: details.custom_kcal_target,
      proteinG: details.custom_protein_g ?? 0,
      fatG: details.custom_fat_g ?? 0,
      carbsG: details.custom_carbs_g ?? 0,
    };
  }
  return macroTargets(kcal, profile.weightKg);
}

/**
 * After every write, the day summary is recomputed from the server's own rows
 * and merged into day_summaries, so a partner's Day page shows the same line
 * for a web-only account as for a phone account. Which columns move depends
 * on who owns the row (see domain/summary.ts): for a phone account only the
 * eaten total, never its steps or active kcal.
 */
async function refreshDaySummary(userId: string, epochDay: number, policy: SummaryPolicy) {
  const [entries, existing] = await Promise.all([
    supabase.from('food_log_entries').select('kcal').eq('owner_id', userId).eq('epoch_day', epochDay).is('deleted_at', null),
    supabase.from('day_summaries').select('target_kcal').eq('owner_id', userId).eq('epoch_day', epochDay).maybeSingle(),
  ]);
  if (entries.error) throw entries.error;
  if (existing.error) throw existing.error;
  const eaten = ((entries.data ?? []) as { kcal: number }[]).reduce((a, r) => a + r.kcal, 0);
  const payload = daySummaryPayload(policy, eaten, (existing.data as { target_kcal: number } | null) ?? null);
  if (!payload) return;
  const { error } = await supabase.from('day_summaries').upsert({ owner_id: userId, epoch_day: epochDay, ...payload });
  if (error) throw error;
}

function invalidateDay(qc: ReturnType<typeof useQueryClient>, userId: string) {
  qc.invalidateQueries({ queryKey: ['day', userId] });
  qc.invalidateQueries({ queryKey: ['week', userId] });
}

const newId = (prefix: string) => `web:${prefix}:${crypto.randomUUID()}`;

export interface LogFoodInput {
  product: FoodProduct;
  grams: number;
  servings: number | null;
  meal: MealSlot;
  epochDay: number;
  summary: SummaryPolicy;
}

/** The web equivalent of DiaryRepository.logProduct: nutrition snapshotted at log time. */
export function useLogFood(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ product: p, grams, servings, meal, epochDay, summary }: LogFoodInput) => {
      const n = forGrams(p.kcalPer100g, p.proteinPer100g, p.fatPer100g, p.carbsPer100g, grams);
      const { error } = await supabase.from('food_log_entries').insert({
        id: newId('f'),
        owner_id: userId,
        epoch_day: epochDay,
        meal,
        name: p.name,
        grams,
        servings,
        kcal: n.kcal,
        protein_g: n.proteinG,
        fat_g: n.fatG,
        carbs_g: n.carbsG,
        fiber_g: scalePer100g(p.fiberPer100g, grams),
        sugars_g: scalePer100g(p.sugarsPer100g, grams),
        salt_g: scalePer100g(p.saltPer100g, grams),
        saturated_fat_g: scalePer100g(p.saturatedFatPer100g, grams),
        logged_at: new Date().toISOString(),
      });
      if (error) throw error;
      await refreshDaySummary(userId!, epochDay, summary);
    },
    onSuccess: () => invalidateDay(qc, userId!),
  });
}

export function useDeleteFood(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, epochDay, summary }: { id: string; epochDay: number; summary: SummaryPolicy }) => {
      const { error } = await supabase.from('food_log_entries').update({ deleted_at: new Date().toISOString() }).eq('id', id);
      if (error) throw error;
      await refreshDaySummary(userId!, epochDay, summary);
    },
    onSuccess: () => invalidateDay(qc, userId!),
  });
}

export function useAddWater(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ ml, epochDay }: { ml: number; epochDay: number }) => {
      const { error } = await supabase
        .from('water_entries')
        .insert({ id: newId('w'), owner_id: userId, epoch_day: epochDay, ml, logged_at: new Date().toISOString() });
      if (error) throw error;
    },
    onSuccess: () => invalidateDay(qc, userId!),
  });
}

/** Undo removes the most recent live glass of the day. */
export function useUndoWater(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ epochDay }: { epochDay: number }) => {
      const { data, error } = await supabase
        .from('water_entries')
        .select('id')
        .eq('owner_id', userId!)
        .eq('epoch_day', epochDay)
        .is('deleted_at', null)
        .order('logged_at', { ascending: false })
        .limit(1);
      if (error) throw error;
      const last = (data ?? [])[0] as { id: string } | undefined;
      if (!last) return;
      const { error: e2 } = await supabase.from('water_entries').update({ deleted_at: new Date().toISOString() }).eq('id', last.id);
      if (e2) throw e2;
    },
    onSuccess: () => invalidateDay(qc, userId!),
  });
}

export function useLogWeight(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ weightKg, epochDay }: { weightKg: number; epochDay: number }) => {
      const { error } = await supabase
        .from('weight_entries')
        .upsert({ owner_id: userId, epoch_day: epochDay, source: 'MANUAL', weight_kg: weightKg });
      if (error) throw error;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['latestWeight', userId] });
      invalidateDay(qc, userId!);
    },
  });
}

/** Ensures today's summary exists for a web-only account (e.g. after onboarding). */
export function useEnsureTodaySummary(userId: string | null) {
  return useMutation({
    mutationFn: async (targetKcal: number) =>
      refreshDaySummary(userId!, todayEpochDay(), { targetKcal, primaryClient: 'web' }),
  });
}
