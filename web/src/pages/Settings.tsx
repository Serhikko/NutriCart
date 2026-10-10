import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type MouseEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useI18n, type Locale } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useFollowed, useMyPartners, useMyProfile, useNewPairingCode, useRemovePartner, useSaveMyName, useUnfollow } from '../lib/queries';
import { useProfileDetails } from '../lib/tracker';
import { ageYears } from '../domain/calories';
import { supabase } from '../lib/supabase';
import { generatePassword, isAcceptablePassword } from '../lib/password';
import { DESKTOP_QUERY, prefersReducedMotion, setNavDirection, useMediaQuery } from '../lib/motion';
import { LargeTitle } from '../components/ui/LargeTitle';
import { Button } from '../components/ui/Button';
import { Icon } from '../components/ui/Icon';
import { Notice } from '../components/ui/Notice';
import { OfflineBanner } from '../components/ui/OfflineBanner';
import { SegmentedControl } from '../components/ui/SegmentedControl';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toast';
import { Initial } from '../components/FollowingList';
import { PairingCodeCard } from '../components/PairingCodeCard';

type AccountNotice = { key: string; email?: string; error?: boolean } | null;

/** The code on screen survives a reload; it is useless to anyone else once redeemed or expired. */
const PAIRING_KEY = 'nutricart.pairing';
function readPairing(): { code: string; expiresAt: number } | null {
  try {
    const raw = localStorage.getItem(PAIRING_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { code: string; expiresAt: number };
    return parsed.expiresAt > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}
function writePairing(value: { code: string; expiresAt: number }) {
  try {
    localStorage.setItem(PAIRING_KEY, JSON.stringify(value));
  } catch {
    /* private mode: the code just does not survive a reload */
  }
}

/**
 * Age from a stored birth date. "YYYY-MM-DD" is read as a local calendar
 * date, as tracker.ts does: new Date("1993-04-21") would be midnight UTC,
 * which is the evening before west of Greenwich, and the age would turn a
 * day late.
 */
function ageFrom(birth: string): number {
  const [y, m, d] = birth.split('-').map(Number);
  return ageYears(new Date(y, (m || 1) - 1, d || 1), new Date());
}

/** Entrance delay (ms) for the .a-* classes: from 300, 60 ms apart, at most 8 steps. */
const delay = (index: number) => ({ '--d': 300 + Math.min(index, 7) * 60 }) as CSSProperties;

interface SectionProps {
  id: string;
  title: ReactNode;
  /** The grey line under the group. */
  footer?: ReactNode;
  index: number;
  children: ReactNode;
}

/**
 * An iOS grouped section: a sentence-case label above, the group (one or
 * more cards), an optional footnote below. The section can take focus, so
 * the desktop index can move the keyboard there too.
 */
function Section({ id, title, footer, index, children }: SectionProps) {
  return (
    <section className="set-sec a-rise" id={id} style={delay(index)} aria-labelledby={`${id}-h`} tabIndex={-1}>
      <h2 className="set-head" id={`${id}-h`}>
        {title}
      </h2>
      {children}
      {footer != null && footer !== false && <p className="set-foot">{footer}</p>}
    </section>
  );
}

interface IndexEntry {
  id: string;
  label: string;
}

/**
 * The desktop section index: anchor links in a sticky column, the section
 * being read marked with a sliding pill (G7). Which one is "being read" is the
 * last whose top has passed a line 30% down the window (the last section once
 * the page is scrolled to the end). A click scrolls there and moves focus.
 */
function SectionIndex({ entries, label }: { entries: IndexEntry[]; label: string }) {
  const desktop = useMediaQuery(DESKTOP_QUERY, false);
  const [active, setActive] = useState(entries[0]?.id ?? '');
  const nav = useRef<HTMLElement>(null);
  const pill = useRef<HTMLSpanElement>(null);
  const lockedUntil = useRef(0);
  const ids = entries.map((e) => e.id).join(',');

  useEffect(() => {
    if (!desktop) return;
    const list = ids.split(',');
    const spy = () => {
      if (Date.now() < lockedUntil.current) return;
      const line = window.innerHeight * 0.3;
      const atEnd = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2;
      let current = list[0];
      for (const id of list) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= line) current = id;
      }
      if (atEnd && window.scrollY > 0) current = list[list.length - 1];
      setActive(current);
    };
    spy();
    window.addEventListener('scroll', spy, { passive: true });
    window.addEventListener('resize', spy);
    return () => {
      window.removeEventListener('scroll', spy);
      window.removeEventListener('resize', spy);
    };
  }, [desktop, ids]);

  // The pill sits under the current link (positions as custom properties; CSS moves it).
  useLayoutEffect(() => {
    const box = nav.current;
    const el = pill.current;
    if (!box || !el) return;
    const link = box.querySelector<HTMLElement>('[aria-current="true"]');
    if (!link || !link.offsetHeight) return;
    el.style.setProperty('--pos', `${link.offsetTop}px`);
    el.style.setProperty('--size', `${link.offsetHeight}px`);
    box.dataset.measured = '';
  }, [active, desktop, ids]);

  const go = (id: string) => (event: MouseEvent<HTMLAnchorElement>) => {
    const target = document.getElementById(id);
    if (!target) return;
    event.preventDefault();
    setActive(id);
    // Let the smooth scroll finish before the scroll position decides again.
    lockedUntil.current = Date.now() + 900;
    // Optional call: jsdom (the tests) has no scrollIntoView, and the index renders at every width.
    target.scrollIntoView?.({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' });
    target.focus({ preventScroll: true });
  };

  return (
    <nav className="set-index" ref={nav} aria-label={label}>
      <span className="set-index-pill" ref={pill} aria-hidden="true" />
      {entries.map((e) => (
        <a key={e.id} href={`#${e.id}`} aria-current={e.id === active ? 'true' : undefined} onClick={go(e.id)}>
          {e.label}
        </a>
      ))}
    </nav>
  );
}

/**
 * A list of people still loading: one row at its final geometry (avatar, name)
 * instead of a word in the list, read once as "Loading…".
 */
function RowsLoading({ label }: { label: string }) {
  return (
    <div className="card set-group set-list">
      <div className="set-row">
        <Skeleton variant="line" width={30} height={30} className="set-skel-avatar" />
        <Skeleton variant="line" width="38%" height={13} label={label} />
      </div>
    </div>
  );
}

/** Name, sharing, language, the account's email, the accounts followed, and the way out. */
export function Settings() {
  const { t, tag, locale, setLocale } = useI18n();
  const { userId, email, isAnonymous, loading } = useSession();
  const profile = useMyProfile(userId);
  const saveName = useSaveMyName(userId);
  const followed = useFollowed(userId);
  const unfollow = useUnfollow(userId);
  const details = useProfileDetails(userId);
  const partners = useMyPartners(userId);
  const removePartner = useRemovePartner(userId);
  const newCode = useNewPairingCode(userId);
  const toast = useToast();
  const [pairing, setPairing] = useState<{ code: string; expiresAt: number } | null>(() => readPairing());
  const [freshCode, setFreshCode] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const [name, setName] = useState('');
  /** The row whose Remove / Stop following was pressed: where focus goes once the list changes. */
  const refocus = useRef<{ section: string; linkId: string; index: number } | null>(null);

  // The countdown under the code; a refresh every 15 seconds is enough.
  useEffect(() => {
    if (!pairing) return;
    const id = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(id);
  }, [pairing]);

  const makeCode = () =>
    newCode.mutate(undefined, {
      onSuccess: (fresh) => {
        setPairing(fresh);
        setFreshCode(true);
        setNow(Date.now());
        writePairing(fresh);
      },
    });
  const minutesLeft = pairing ? Math.max(0, Math.ceil((pairing.expiresAt - now) / 60_000)) : 0;
  const [emailText, setEmailText] = useState('');
  const [passwordText, setPasswordText] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<AccountNotice>(null);

  useEffect(() => {
    if (profile.data !== undefined) setName(profile.data);
  }, [profile.data]);

  const canCopy = typeof navigator !== 'undefined' && typeof navigator.clipboard?.writeText === 'function';
  const copyCode = () => {
    if (!pairing) return;
    navigator.clipboard.writeText(pairing.code).then(
      () => toast({ text: t('settings.code_copied') }),
      () => undefined,
    );
  };

  const forget = async () => {
    await supabase.auth.signOut();
    window.location.href = '/';
  };

  const address = emailText.trim().toLowerCase();
  const emailValid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(address);
  const passwordValid = isAcceptablePassword(passwordText);

  const fillPassword = () => {
    setPasswordText(generatePassword());
    setShowPassword(true);
    setNotice(null);
  };

  /**
   * Links the address and password to this (anonymous) account; GoTrue mails
   * a confirmation. The link may open in any browser (Gmail's own, say):
   * that only confirms the address. Signing in where you want is then a
   * matter of the password, no link involved.
   */
  const keepAccount = async () => {
    setBusy(true);
    const { error } = await supabase.auth.updateUser({ email: address, password: passwordText }, { emailRedirectTo: window.location.origin });
    setBusy(false);
    setNotice(error ? { key: 'settings.account_error', error: true } : { key: 'settings.account_sent', email: address });
  };

  /** Email and password, for an account linked on the phone or here. */
  const signIn = async () => {
    setBusy(true);
    const { error } = await supabase.auth.signInWithPassword({ email: address, password: passwordText });
    setBusy(false);
    if (!error) setNotice({ key: 'settings.account_signed_in_now', email: address });
    else if (/not confirmed/i.test(error.message)) setNotice({ key: 'settings.account_unconfirmed', error: true });
    else if (/invalid login/i.test(error.message)) setNotice({ key: 'settings.account_bad_password', error: true });
    else setNotice({ key: 'settings.account_error', error: true });
  };

  /** The fallback for a forgotten password: a sign-in link by email. */
  const sendLink = async () => {
    setBusy(true);
    const { error } = await supabase.auth.signInWithOtp({
      email: address,
      options: { emailRedirectTo: window.location.origin, shouldCreateUser: false },
    });
    setBusy(false);
    if (!error) setNotice({ key: 'settings.account_magic_sent', email: address });
    else if (/signups? not allowed/i.test(error.message)) setNotice({ key: 'settings.account_unknown', error: true });
    else setNotice({ key: 'settings.account_error', error: true });
  };

  /** A signed-in account without a password (magic link) can set one here. */
  const setPassword = async () => {
    setBusy(true);
    const { error } = await supabase.auth.updateUser({ password: passwordText });
    setBusy(false);
    setNotice(error ? { key: 'settings.account_error', error: true } : { key: 'settings.account_password_set' });
  };

  const nf = useMemo(() => new Intl.NumberFormat(tag), [tag]);

  // Remove and Stop following take their own row away, and the keyboard focus would go with it
  // (to the top of the page). Once the list no longer has the row, focus goes to the button of
  // the row now in its place (or of the last row, or to the section when the list is empty);
  // when the removal failed, back to the same button. Focus the person has since put elsewhere
  // is left alone.
  useEffect(() => {
    const want = refocus.current;
    if (!want) return;
    const isPartners = want.section === 'set-partners';
    const mutation = isPartners ? removePartner : unfollow;
    if (mutation.isPending) return;
    const rows = (isPartners ? partners.data : followed.data) ?? [];
    const gone = !rows.some((r) => r.linkId === want.linkId);
    // Removed, but the refetched list has not arrived yet.
    if (!gone && !mutation.isError) return;
    refocus.current = null;
    const section = document.getElementById(want.section);
    const active = document.activeElement;
    if (!section || (active && active !== document.body && !section.contains(active))) return;
    const buttons = section.querySelectorAll<HTMLButtonElement>('li.set-row button');
    (buttons[Math.min(want.index, buttons.length - 1)] ?? section).focus();
  }, [partners.data, followed.data, removePartner.isPending, removePartner.isError, unfollow.isPending, unfollow.isError]);

  // The password, with the show/hide eye inside its field and Generate on its own row under it.
  const passwordField = (
    <div className="set-field">
      <label htmlFor="password" className="field-label">
        {t('settings.account_password')}
      </label>
      <div className="field set-password">
        <input
          id="password"
          type={showPassword ? 'text' : 'password'}
          className={showPassword ? 'is-shown' : undefined}
          autoComplete="new-password"
          value={passwordText}
          onChange={(e) => {
            setPasswordText(e.target.value);
            setNotice(null);
          }}
        />
        <button type="button" className="field-eye" onClick={() => setShowPassword((v) => !v)} aria-label={t('settings.account_show')} aria-pressed={showPassword}>
          <Icon name={showPassword ? 'eyeOff' : 'eye'} size="sm" />
        </button>
      </div>
      <Button type="button" variant="fill" size="sm" icon="sparkle" className="set-generate" onClick={fillPassword}>
        {t('settings.account_generate')}
      </Button>
    </div>
  );

  const signedIn = !isAnonymous && Boolean(email);

  const entries: IndexEntry[] = [
    { id: 'set-share', label: t('settings.share') },
    { id: 'set-name', label: t('settings.name') },
    ...(details.data ? [{ id: 'set-profile', label: t('settings.profile') }] : []),
    { id: 'set-account', label: t('settings.account') },
    { id: 'set-language', label: t('settings.language') },
    { id: 'set-following', label: t('settings.following') },
  ];

  if (loading) {
    return (
      <div className="set">
        <LargeTitle title={t('settings.title')} />
        <div className="set-skel">
          <Skeleton variant="line" width={120} height={13} label={t('loading')} />
          <Skeleton variant="block" height={190} />
          <Skeleton variant="line" width={90} height={13} />
          <Skeleton variant="block" height={96} />
          <Skeleton variant="line" width={110} height={13} />
          <Skeleton variant="block" height={240} />
        </div>
      </div>
    );
  }

  let i = 0;
  return (
    <div className="set">
      <LargeTitle title={t('settings.title')} />
      <OfflineBanner />

      <div className="set-grid">
        <SectionIndex entries={entries} label={t('settings.title')} />

        <div className="set-main">
          {userId && (
            <>
              <Section id="set-share" title={t('settings.share')} footer={t('settings.share_hint')} index={i++}>
                <div className="card set-group">
                  <PairingCodeCard
                    code={pairing?.code ?? null}
                    minutesLeft={minutesLeft}
                    expired={pairing !== null && minutesLeft === 0}
                    fresh={freshCode}
                    onNew={makeCode}
                    pending={newCode.isPending}
                    failed={newCode.isError}
                    onCopy={canCopy ? copyCode : undefined}
                    copyLabel={t('settings.code_copy')}
                  />
                </div>
              </Section>

              <Section id="set-partners" title={t('settings.partners')} index={i++}>
                {partners.data?.length ? (
                  <ul className="card set-group set-list">
                    {partners.data.map((p, index) => (
                      <li className="set-row" key={p.linkId}>
                        <Initial name={p.name} />
                        <span className="set-row-main" id={`partner-${p.linkId}`}>
                          {p.name}
                        </span>
                        <Button
                          variant="plain"
                          className="danger"
                          onClick={() => {
                            refocus.current = { section: 'set-partners', linkId: p.linkId, index };
                            removePartner.mutate(p.linkId);
                          }}
                          disabled={removePartner.isPending}
                          aria-describedby={`partner-${p.linkId}`}
                        >
                          {t('settings.partner_remove')}
                        </Button>
                      </li>
                    ))}
                  </ul>
                ) : partners.isLoading ? (
                  <RowsLoading label={t('loading')} />
                ) : (
                  <div className="card set-group set-list">
                    <p className="set-row set-empty">{t('settings.partners_none')}</p>
                  </div>
                )}
                {removePartner.isError && <Notice tone="error" title={t('error.generic')} />}
              </Section>
            </>
          )}

          <Section id="set-name" title={t('settings.name')} index={i++}>
            <div className="card set-group">
              <div className="set-inline">
                <div className="field">
                  <input id="myname" type="text" maxLength={40} autoComplete="nickname" aria-labelledby="set-name-h" value={name} onChange={(e) => setName(e.target.value)} />
                </div>
                <Button variant="ink" size="sm" onClick={() => saveName.mutate(name)} disabled={!name.trim() || saveName.isPending}>
                  {t('settings.save')}
                </Button>
              </div>
              {saveName.isError && <Notice tone="error" title={t('welcome.failed')} />}
            </div>
            <p className={saveName.isSuccess ? 'set-foot' : 'set-foot is-idle'} role="status">
              {saveName.isSuccess ? t('settings.saved') : ''}
            </p>
          </Section>

          {details.data && (
            <Section id="set-profile" title={t('settings.profile')} index={i++}>
              <div className="card set-group set-list">
                <p className="set-row set-summary">
                  {t(details.data.sex === 'MALE' ? 'ob.male' : 'ob.female')} · {ageFrom(details.data.birth_date)} · {details.data.height_cm} {t('unit.cm')} ·{' '}
                  {t(`ob.goal.${details.data.goal}`)}
                  {details.data.custom_kcal_target != null ? ` · ${nf.format(details.data.custom_kcal_target)} ${t('unit.kcal')}` : ''}
                </p>
                {details.data.primary_client === 'phone' ? (
                  <p className="set-row footnote">{t('settings.profile_phone')}</p>
                ) : (
                  <Link className="set-row set-link" to="/me/onboarding" viewTransition onClick={() => setNavDirection('forward')}>
                    <Icon name="person" size="sm" className="set-row-icon" />
                    <span className="set-row-main">{t('settings.profile_edit')}</span>
                    <Icon name="right" size="xs" className="chev" />
                  </Link>
                )}
              </div>
            </Section>
          )}

          <Section id="set-account" title={t('settings.account')} index={i++}>
            <div className="card set-group set-account">
              {signedIn ? (
                <>
                  <p className="set-signed">
                    <Icon name="person" size="sm" />
                    <span>{t('settings.account_signed_in', { email: email! })}</span>
                  </p>
                  <p className="footnote">{t('settings.account_password_hint')}</p>
                  {passwordField}
                  <div className="set-actions">
                    <Button variant="ink" onClick={setPassword} disabled={!passwordValid || busy}>
                      {t('settings.account_password_save')}
                    </Button>
                  </div>
                </>
              ) : (
                <>
                  <p className="footnote">{t('settings.account_hint')}</p>
                  <div className="set-field">
                    <label htmlFor="email" className="field-label">
                      {t('settings.account_email')}
                    </label>
                    <div className="field">
                      <input
                        id="email"
                        type="email"
                        autoComplete="email"
                        inputMode="email"
                        value={emailText}
                        onChange={(e) => {
                          setEmailText(e.target.value);
                          setNotice(null);
                        }}
                      />
                    </div>
                  </div>
                  {passwordField}
                  <div className="set-actions">
                    <Button variant="ink" onClick={signIn} disabled={!emailValid || !passwordValid || busy}>
                      {t('settings.account_signin')}
                    </Button>
                    <Button variant="fill" onClick={keepAccount} disabled={!emailValid || !passwordValid || busy}>
                      {t('settings.account_keep')}
                    </Button>
                    <Button variant="plain" className="set-link-btn" onClick={sendLink} disabled={!emailValid || busy}>
                      {t('settings.account_link')}
                    </Button>
                  </div>
                </>
              )}
              {notice && (
                <Notice
                  tone={notice.error ? 'error' : 'info'}
                  role={notice.error ? 'alert' : 'status'}
                  title={t(notice.key, notice.email ? { email: notice.email } : undefined)}
                />
              )}
            </div>
          </Section>

          <Section id="set-language" title={t('settings.language')} index={i++}>
            <div className="card set-group set-lang">
              <SegmentedControl<Locale>
                labelledBy="set-language-h"
                options={[
                  { value: 'en', label: 'English' },
                  { value: 'uk', label: 'Українська' },
                ]}
                value={locale}
                onChange={setLocale}
              />
            </div>
          </Section>

          {/* The privacy note describes following, so it is this region's footnote: a screen reader
              moving by region hears it with the list, not as the start of Forget this device. */}
          <Section id="set-following" title={t('settings.following')} footer={t('settings.privacy')} index={i++}>
            {followed.data?.length ? (
              <ul className="card set-group set-list">
                {followed.data.map((a, index) => (
                  <li className="set-row" key={a.linkId}>
                    <Initial name={a.ownerName} />
                    <Link
                      className="set-row-main set-person"
                      id={`follow-${a.linkId}`}
                      to={`/a/${a.ownerId}/day`}
                      viewTransition
                      onClick={() => setNavDirection('forward')}
                    >
                      {a.ownerName}
                    </Link>
                    <Button
                      variant="plain"
                      className="danger"
                      onClick={() => {
                        refocus.current = { section: 'set-following', linkId: a.linkId, index };
                        unfollow.mutate(a.linkId);
                      }}
                      disabled={unfollow.isPending}
                      aria-describedby={`follow-${a.linkId}`}
                    >
                      {t('settings.unfollow')}
                    </Button>
                  </li>
                ))}
              </ul>
            ) : followed.isLoading && userId ? (
              <RowsLoading label={t('loading')} />
            ) : (
              <div className="card set-group set-list">
                <p className="set-row set-empty">{t('settings.none')}</p>
              </div>
            )}
            {unfollow.isError && <Notice tone="error" title={t('error.generic')} />}
          </Section>

          <section className="set-sec set-device a-rise" style={delay(i++)} aria-label={t('settings.signout')}>
            <button type="button" className="card set-signout" onClick={forget}>
              <Icon name="signOut" size="sm" />
              <span>{t('settings.signout')}</span>
            </button>
            <p className="set-foot">{t(isAnonymous ? 'settings.signout_hint' : 'settings.signout_hint_email')}</p>
          </section>
        </div>
      </div>
    </div>
  );
}
