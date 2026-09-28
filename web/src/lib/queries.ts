import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { supabase } from './supabase';
import { lastDays } from './dates';
import { PAIRING_VALIDITY_MINUTES, generatePairingCode, hashPairingCode, normalizePairingCode } from './pairing';
import type { DaySummary, FoodLogEntry, Nudge, WaterEntry, WeightEntry } from './diary';

/**
 * Every read the site does, as React Query hooks, plus the three writes
 * (redeem a code, send a nudge, save a name). Realtime subscriptions only
 * invalidate the matching query; the data always comes from a fresh select,
 * so RLS is applied by PostgREST on every row the screen shows.
 */

export interface FollowedAccount {
  linkId: string;
  ownerId: string;
  ownerName: string;
}

const ownerName = (row: { owner: { display_name: string | null } | null }, fallback: string) =>
  row.owner?.display_name?.trim() || fallback;

export function useFollowed(userId: string | null) {
  return useQuery({
    queryKey: ['followed', userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<FollowedAccount[]> => {
      const { data, error } = await supabase
        .from('partner_links')
        .select('id, owner_id, owner:profiles!partner_links_owner_profile_fkey(display_name)')
        .eq('partner_id', userId!)
        .order('created_at');
      if (error) throw error;
      return (data ?? []).map((row) => ({
        linkId: row.id as string,
        ownerId: row.owner_id as string,
        ownerName: ownerName(row as never, 'NutriCart'),
      }));
    },
  });
}

export function useMyProfile(userId: string | null) {
  return useQuery({
    queryKey: ['profile', userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<string> => {
      const { data, error } = await supabase.from('profiles').select('display_name').eq('user_id', userId!).maybeSingle();
      if (error) throw error;
      return data?.display_name ?? '';
    },
  });
}

export function useSaveMyName(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      const { error } = await supabase.from('profiles').upsert({ user_id: userId, display_name: name.trim().slice(0, 40) });
      if (error) throw error;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profile', userId] }),
  });
}

/** Error codes redeem_pairing_code raises, mapped to the welcome screen's messages. */
export type RedeemFailure = 'bad_code' | 'own_code' | 'too_many' | 'offline' | 'failed';

/** The people who redeemed this account's code (the phone's "Who can see your day"). */
export interface MyPartner {
  linkId: string;
  name: string;
  since: string;
}

export function useMyPartners(userId: string | null) {
  return useQuery({
    queryKey: ['partners', userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<MyPartner[]> => {
      const { data, error } = await supabase
        .from('partner_links')
        .select('id, partner_id, created_at, partner:profiles!partner_links_partner_profile_fkey(display_name)')
        .eq('owner_id', userId!)
        .order('created_at');
      if (error) throw error;
      return (data ?? []).map((row) => {
        const r = row as unknown as { id: string; partner_id: string; created_at: string; partner: { display_name: string | null } | null };
        return { linkId: r.id, name: r.partner?.display_name?.trim() || r.partner_id.slice(0, 8), since: r.created_at };
      });
    },
  });
}

export function useRemovePartner(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (linkId: string) => {
      const { error } = await supabase.from('partner_links').delete().eq('id', linkId);
      if (error) throw error;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['partners', userId] }),
  });
}

/**
 * A fresh pairing code, as the phone's Settings makes one: older unused
 * codes of this account are discarded first, so exactly one works at a
 * time; only the hash goes to the server, the plain code stays on screen.
 */
export function useNewPairingCode(userId: string | null) {
  return useMutation({
    mutationFn: async (): Promise<{ code: string; expiresAt: number }> => {
      const { error: clearError } = await supabase.from('pairing_codes').delete().eq('owner_id', userId!);
      if (clearError) throw clearError;
      const code = generatePairingCode();
      const expiresAt = Date.now() + PAIRING_VALIDITY_MINUTES * 60_000;
      const { error } = await supabase
        .from('pairing_codes')
        .insert({ code_hash: await hashPairingCode(code), owner_id: userId, expires_at: new Date(expiresAt).toISOString() });
      if (error) throw error;
      return { code, expiresAt };
    },
  });
}

export function useRedeemCode(userId: string | null) {
  const qc = useQueryClient();
  return useMutation<FollowedAccount, RedeemFailure, string>({
    mutationFn: async (rawCode: string) => {
      const code = normalizePairingCode(rawCode);
      const { data, error } = await supabase.rpc('redeem_pairing_code', { p_code: code });
      if (error) {
        if (error.code === '22023') throw 'own_code';
        if (error.code === '54000') throw 'too_many';
        if (error.message?.toLowerCase().includes('fetch')) throw 'offline';
        throw 'failed';
      }
      const row = (data as { owner_id: string; display_name: string }[] | null)?.[0];
      if (!row) throw 'bad_code';
      return { linkId: '', ownerId: row.owner_id, ownerName: row.display_name || 'NutriCart' };
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['followed', userId] }),
  });
}

export function useUnfollow(userId: string | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (linkId: string) => {
      const { error } = await supabase.from('partner_links').delete().eq('id', linkId);
      if (error) throw error;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['followed', userId] }),
  });
}

export function useDay(ownerId: string, epochDay: number) {
  return useQuery({
    queryKey: ['day', ownerId, epochDay],
    queryFn: async () => {
      const [entries, water, summary] = await Promise.all([
        supabase.from('food_log_entries').select('*').eq('owner_id', ownerId).eq('epoch_day', epochDay).order('logged_at'),
        supabase.from('water_entries').select('*').eq('owner_id', ownerId).eq('epoch_day', epochDay),
        supabase.from('day_summaries').select('*').eq('owner_id', ownerId).eq('epoch_day', epochDay).maybeSingle(),
      ]);
      if (entries.error) throw entries.error;
      if (water.error) throw water.error;
      if (summary.error) throw summary.error;
      return {
        entries: (entries.data ?? []) as FoodLogEntry[],
        water: (water.data ?? []) as WaterEntry[],
        summary: (summary.data ?? null) as DaySummary | null,
      };
    },
    refetchInterval: 60_000, // belt and braces if the realtime channel drops
  });
}

export function useWeek(ownerId: string, today: number) {
  const days = lastDays(7, today);
  return useQuery({
    queryKey: ['week', ownerId, today],
    queryFn: async () => {
      const [summaries, entries, weights] = await Promise.all([
        supabase.from('day_summaries').select('*').eq('owner_id', ownerId).gte('epoch_day', days[0]).lte('epoch_day', today),
        supabase
          .from('food_log_entries')
          .select('epoch_day, kcal, deleted_at')
          .eq('owner_id', ownerId)
          .gte('epoch_day', days[0])
          .lte('epoch_day', today)
          .is('deleted_at', null),
        supabase.from('weight_entries').select('*').eq('owner_id', ownerId).gte('epoch_day', today - 30).order('epoch_day'),
      ]);
      if (summaries.error) throw summaries.error;
      if (entries.error) throw entries.error;
      if (weights.error) throw weights.error;
      return {
        days,
        summaries: (summaries.data ?? []) as DaySummary[],
        entries: (entries.data ?? []) as Pick<FoodLogEntry, 'epoch_day' | 'kcal' | 'deleted_at'>[],
        weights: (weights.data ?? []) as WeightEntry[],
      };
    },
  });
}

export function useMyNudges(ownerId: string, userId: string | null) {
  return useQuery({
    queryKey: ['nudges', ownerId, userId],
    enabled: Boolean(userId),
    queryFn: async (): Promise<Nudge[]> => {
      const { data, error } = await supabase
        .from('nudges')
        .select('*')
        .eq('owner_id', ownerId)
        .eq('from_id', userId!)
        .order('created_at', { ascending: false })
        .limit(10);
      if (error) throw error;
      return (data ?? []) as Nudge[];
    },
  });
}

export function useSendNudge(ownerId: string, userId: string | null, fromName: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (text: string) => {
      const { error } = await supabase
        .from('nudges')
        .insert({ owner_id: ownerId, from_id: userId, from_name: fromName.trim().slice(0, 40), text: text.trim().slice(0, 500) });
      if (error) throw error;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['nudges', ownerId, userId] }),
  });
}

/**
 * One Realtime channel per followed owner: any change to their diary, water,
 * summaries or nudges invalidates the day, week and nudge queries. RLS filters
 * the events server-side, so a partner only ever hears about rows they may read.
 */
export function useOwnerRealtime(ownerId: string | null) {
  const qc = useQueryClient();
  useEffect(() => {
    if (!ownerId) return;
    const invalidate = () => {
      qc.invalidateQueries({ queryKey: ['day', ownerId] });
      qc.invalidateQueries({ queryKey: ['week', ownerId] });
      qc.invalidateQueries({ queryKey: ['nudges', ownerId] });
    };
    const channel = supabase.channel(`owner-${ownerId}`);
    for (const table of ['food_log_entries', 'water_entries', 'day_summaries', 'nudges']) {
      channel.on('postgres_changes', { event: '*', schema: 'public', table, filter: `owner_id=eq.${ownerId}` }, invalidate);
    }
    channel.subscribe();
    return () => {
      supabase.removeChannel(channel);
    };
  }, [ownerId, qc]);
}
