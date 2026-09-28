import { createClient, type SupabaseClient } from '@supabase/supabase-js';

/**
 * One client for the whole site. The anon key is public by design: what a
 * signed-in user may read or write is decided by the row-level security
 * policies in supabase/migrations, never by anything in this bundle.
 */
const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const isConfigured = Boolean(url && anonKey);

export const supabase: SupabaseClient = createClient(
  url ?? 'https://unconfigured.invalid',
  anonKey ?? 'unconfigured',
  { auth: { persistSession: true, autoRefreshToken: true } },
);
