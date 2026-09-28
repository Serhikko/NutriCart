import { useSession } from './session';
import { targetProfileFor, targetsFor, useLatestWeight, useProfileDetails } from './tracker';

/** Everything the "me" pages need about the signed-in user's targets on a day. */
export function useMyTargets(epochDay: number) {
  const { userId } = useSession();
  const details = useProfileDetails(userId);
  const weight = useLatestWeight(userId);
  const profile = targetProfileFor(details.data, weight.data, epochDay);
  const targets = targetsFor(profile, details.data);
  return {
    userId,
    loading: details.isLoading || weight.isLoading,
    onboarded: Boolean(details.data),
    details: details.data ?? null,
    weightKg: weight.data ?? null,
    profile,
    targets,
  };
}
