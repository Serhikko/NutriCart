import { useSession } from './session';
import { targetProfileFor, targetsFor, useLatestWeight, useProfileDetails } from './tracker';
import type { PrimaryClient, SummaryPolicy } from '../domain/summary';

/** Everything the "me" pages need about the signed-in user's targets on a day. */
export function useMyTargets(epochDay: number) {
  const { userId } = useSession();
  const details = useProfileDetails(userId);
  const weight = useLatestWeight(userId);
  const profile = targetProfileFor(details.data, weight.data, epochDay);
  const targets = targetsFor(profile, details.data);
  const primaryClient: PrimaryClient = details.data?.primary_client ?? 'web';
  const summary: SummaryPolicy = { targetKcal: targets?.kcal ?? null, primaryClient };
  return {
    userId,
    loading: details.isLoading || weight.isLoading,
    onboarded: Boolean(details.data),
    details: details.data ?? null,
    weightKg: weight.data ?? null,
    profile,
    targets,
    /** Who publishes day_summaries for this account: the phone, or this website. */
    primaryClient,
    summary,
  };
}
