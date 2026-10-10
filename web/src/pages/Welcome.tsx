import { useState, type CSSProperties, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { lastRedeemDetail, useDay, useFollowed, useMyProfile, useRedeemCode, useSaveMyName } from '../lib/queries';
import { isWellFormedPairingCode, normalizePairingCode } from '../lib/pairing';
import { useProfileDetails } from '../lib/tracker';
import { useMyTargets } from '../lib/myTargets';
import { dayLine, sumKcal } from '../lib/diary';
import { todayEpochDay } from '../lib/dates';
import { setNavDirection, useRouteFirstOpen } from '../lib/motion';
import { useCompactTitle } from '../components/ui/LargeTitle';
import { Button, ButtonLink } from '../components/ui/Button';
import { Notice } from '../components/ui/Notice';
import { Ring } from '../components/ui/Ring';
import { Digits } from '../components/ui/Digits';
import { Skeleton } from '../components/ui/Skeleton';
import { PairingCodeInput } from '../components/PairingCodeInput';
import { FollowingList } from '../components/FollowingList';

/** Entrance delay (ms) for the .a-* classes. */
const delay = (ms: number) => ({ '--d': ms }) as CSSProperties;

// The app icon's ring is open from 12 o'clock round to 10 o'clock.
const LOGO_P = 0.83;

/** The app icon, drawn live: its Ember ring sweeps into place on the first open (W1). */
function Logo({ sweep }: { sweep: boolean }) {
  return (
    <span className="wl-logo a-pop" style={delay(40)} aria-hidden="true">
      <Ring value={LOGO_P} target={1} size={64} stroke={9} sweep={sweep} delay={120} />
    </span>
  );
}

/**
 * Today at a glance on the "Track your own day" card, once a profile exists:
 * a small ring of today and what is left ("870 kcal left").
 */
function TodayGlance({ userId, first }: { userId: string; first: boolean }) {
  const { t } = useI18n();
  const today = todayEpochDay();
  const day = useDay(userId, today);
  const { targets } = useMyTargets(today);
  if (!day.data) return null;
  const line = dayLine(day.data.summary, sumKcal(day.data.entries));
  const target = line.target ?? targets?.kcal ?? null;
  if (target === null || target <= 0) return null;
  const left = target - line.eaten;
  return (
    <div className="wl-glance a-fade-up" style={delay(380)}>
      <Ring value={line.eaten} target={target} size={64} stroke={8} sweep={first} delay={420} />
      <p className="wl-glance-num">
        <Digits value={Math.abs(left)} enter={first} delay={460} className="num" />
        <span className="wl-glance-cap">{t(left < 0 ? 'day.over_caption' : 'day.left')}</span>
      </p>
    </div>
  );
}

/**
 * Home: the brand, the way into your own tracker, the code that lets you
 * follow someone's day, and the accounts already followed. On a desktop the
 * brand and your own tracker lead on the left; following is on the right.
 */
export function Welcome() {
  const { t } = useI18n();
  const { userId, loading, error } = useSession();
  const profile = useMyProfile(userId);
  const saveName = useSaveMyName(userId);
  const redeem = useRedeemCode(userId);
  const details = useProfileDetails(userId);
  const navigate = useNavigate();
  const first = useRouteFirstOpen();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  useCompactTitle(t('app.name'));

  const normalized = normalizePairingCode(code);
  const wellFormed = isWellFormedPairingCode(normalized);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!wellFormed) return;
    const chosen = name.trim() || profile.data || '';
    if (chosen && chosen !== profile.data) await saveName.mutateAsync(chosen);
    redeem.mutate(normalized, {
      onSuccess: (account) => {
        // Following someone is a drill-in: their day slides in from the right.
        setNavDirection('forward');
        navigate(`/a/${account.ownerId}/day`, { viewTransition: true });
      },
    });
  };

  const header = (
    <header className="wl-head">
      <div className="wl-brand">
        <Logo sweep={first} />
        <h1 className="large-title title-clip a-title" style={delay(30)}>
          <span>{t('app.name')}</span>
        </h1>
      </div>
      <p className="wl-tagline a-fade-up" style={delay(120)}>
        {t('welcome.tagline')}
      </p>
    </header>
  );

  if (error === 'not_configured') {
    return (
      <div className="wl">
        {header}
        <Notice tone="error" title={t('welcome.not_configured')} />
      </div>
    );
  }

  const sessionLoading = loading || (Boolean(userId) && details.isLoading);
  const onboarded = Boolean(details.data);
  const failed = redeem.isError;

  return (
    <div className="wl">
      <div className="wl-lead">
        {header}

        {sessionLoading ? (
          <div className="card wl-track wl-skel">
            <Skeleton variant="line" width="60%" height={22} label={t('loading')} />
            <Skeleton variant="line" />
            <Skeleton variant="line" width="80%" />
            <Skeleton variant="block" height={54} />
          </div>
        ) : (
          <section className="card wl-track a-rise" style={delay(300)} aria-labelledby="wl-track">
            <h2 className="wl-card-title" id="wl-track">
              {t('welcome.track_title')}
            </h2>
            {onboarded && userId && <TodayGlance userId={userId} first={first} />}
            <p className="wl-intro">{t('welcome.track_intro')}</p>
            <ButtonLink
              variant="ink"
              size="lg"
              to={onboarded ? '/me/day' : '/me/onboarding'}
              viewTransition
              onClick={() => setNavDirection('forward')}
            >
              {onboarded ? t('welcome.track_open') : t('welcome.track_start')}
            </ButtonLink>
            {!onboarded && (
              <p className="footnote wl-signin">
                {t('welcome.track_signin')}{' '}
                <Link to="/settings" viewTransition onClick={() => setNavDirection('forward')}>
                  {t('nav.settings')}
                </Link>
              </p>
            )}
          </section>
        )}
      </div>

      <div className="wl-side">
        <form className="card wl-follow a-rise" style={delay(360)} onSubmit={onSubmit} aria-labelledby="wl-follow" noValidate>
          <h2 className="wl-card-title" id="wl-follow">
            {t('welcome.title')}
          </h2>
          <p className="footnote wl-follow-intro">{t('welcome.intro')}</p>

          <PairingCodeInput
            label={t('welcome.code')}
            value={code}
            onChange={setCode}
            invalid={failed && (redeem.error === 'bad_code' || redeem.error === 'own_code') && redeem.variables === normalized}
            describedBy={failed ? 'wl-error' : undefined}
          />
          {failed && (
            <Notice
              id="wl-error"
              tone="error"
              title={t(`welcome.${redeem.error}`)}
              detail={redeem.error === 'failed' ? lastRedeemDetail : undefined}
            />
          )}

          <div className="wl-name">
            <label htmlFor="name" className="field-label">
              {t('welcome.name')}
            </label>
            <div className="field">
              <input
                id="name"
                type="text"
                maxLength={40}
                autoComplete="nickname"
                value={name}
                placeholder={profile.data || ''}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
          </div>

          <Button type="submit" variant="ink" size="lg" className="wl-connect" disabled={loading || redeem.isPending || !wellFormed}>
            {redeem.isPending ? t('welcome.connecting') : t('welcome.connect')}
          </Button>
        </form>

        {userId && <FollowingSection userId={userId} />}
      </div>
    </div>
  );
}

/** "You follow": the followed accounts as an inset list; nothing until there is one. */
function FollowingSection({ userId }: { userId: string }) {
  const { t } = useI18n();
  const followed = useFollowed(userId);
  if (!followed.data?.length) return null;
  return (
    <section className="wl-following a-rise" style={delay(420)} aria-labelledby="wl-following">
      <h2 className="wl-sec-title" id="wl-following">
        {t('welcome.linked')}
      </h2>
      <FollowingList userId={userId} variant="list" />
    </section>
  );
}
