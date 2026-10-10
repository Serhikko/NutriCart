/**
 * What the website writes to day_summaries after a diary change. Which
 * client publishes the row is the account's choice (profile_details.
 * primary_client): a phone account's summary carries steps and active kcal
 * the website cannot know, so the website moves only eaten_kcal there and
 * leaves the phone's numbers alone.
 */

export type PrimaryClient = 'phone' | 'web';

export interface SummaryPolicy {
  /** The web-computed target for the day; null while the profile is incomplete. */
  targetKcal: number | null;
  primaryClient: PrimaryClient;
}

/** The columns to upsert (merged into the existing row), or null to write nothing. */
export function daySummaryPayload(
  policy: SummaryPolicy,
  eatenKcal: number,
  existing: { target_kcal: number } | null,
): { eaten_kcal: number; target_kcal?: number } | null {
  const eaten = Math.round(eatenKcal);
  if (policy.primaryClient === 'phone') {
    // The phone's row wins; only the eaten total is refreshed so a partner
    // sees the web edit before the phone syncs. Its own target goes back
    // unchanged: the write is an upsert, and Postgres checks the insert half
    // (target_kcal is NOT NULL) before it finds the row to update, so
    // eaten_kcal alone failed every time. With no row yet, the web target
    // stands in until the phone publishes its own (with activity).
    if (existing) return { eaten_kcal: eaten, target_kcal: existing.target_kcal };
    if (policy.targetKcal === null) return null;
    return { eaten_kcal: eaten, target_kcal: Math.round(policy.targetKcal) };
  }
  if (policy.targetKcal === null) return null;
  return { eaten_kcal: eaten, target_kcal: Math.round(policy.targetKcal) };
}
