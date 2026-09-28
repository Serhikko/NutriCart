import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n, type Locale } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useFollowed, useMyProfile, useSaveMyName, useUnfollow } from '../lib/queries';
import { useProfileDetails } from '../lib/tracker';
import { ageYears } from '../domain/calories';
import { supabase } from '../lib/supabase';

type AccountNotice = { key: string; email?: string; error?: boolean } | null;

/** Name, language, the account's email, the accounts followed, and the way out. */
export function Settings() {
  const { t, locale, setLocale } = useI18n();
  const { userId, email, isAnonymous } = useSession();
  const profile = useMyProfile(userId);
  const saveName = useSaveMyName(userId);
  const followed = useFollowed(userId);
  const unfollow = useUnfollow(userId);
  const details = useProfileDetails(userId);
  const [name, setName] = useState('');
  const [emailText, setEmailText] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<AccountNotice>(null);

  useEffect(() => {
    if (profile.data !== undefined) setName(profile.data);
  }, [profile.data]);

  const forget = async () => {
    await supabase.auth.signOut();
    window.location.href = '/';
  };

  const address = emailText.trim().toLowerCase();
  const emailValid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(address);

  /** Links the address to this (anonymous) account; GoTrue mails a confirmation. */
  const keepAccount = async () => {
    setBusy(true);
    const { error } = await supabase.auth.updateUser({ email: address }, { emailRedirectTo: window.location.origin });
    setBusy(false);
    setNotice(error ? { key: 'settings.account_error', error: true } : { key: 'settings.account_sent', email: address });
  };

  /** A magic link for an account that already has this email (linked on the phone or here). */
  const signIn = async () => {
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

  return (
    <>
      <h1>{t('settings.title')}</h1>

      <section className="card">
        <label htmlFor="myname" className="muted" style={{ fontSize: '0.85rem' }}>
          {t('settings.name')}
        </label>
        <input id="myname" type="text" maxLength={40} value={name} onChange={(e) => setName(e.target.value)} style={{ margin: '6px 0 10px' }} />
        <div className="row">
          <span className="muted" style={{ fontSize: '0.85rem' }}>
            {saveName.isSuccess ? t('settings.saved') : ''}
          </span>
          <button onClick={() => saveName.mutate(name)} disabled={!name.trim() || saveName.isPending}>
            {t('settings.save')}
          </button>
        </div>
      </section>

      {details.data && (
        <section className="card">
          <h2 style={{ marginTop: 0, fontSize: '1rem' }}>{t('settings.profile')}</h2>
          <p className="muted" style={{ margin: '0 0 10px', fontSize: '0.9rem' }}>
            {t(details.data.sex === 'MALE' ? 'ob.male' : 'ob.female')} · {ageYears(new Date(details.data.birth_date), new Date())} · {details.data.height_cm} cm · {t(`ob.goal.${details.data.goal}`)}
            {details.data.custom_kcal_target != null ? ` · ${details.data.custom_kcal_target} kcal` : ''}
          </p>
          {details.data.primary_client === 'phone' ? (
            <p className="muted" style={{ margin: 0, fontSize: '0.85rem' }}>{t('settings.profile_phone')}</p>
          ) : (
            <Link to="/me/onboarding"><button className="ghost" style={{ paddingLeft: 0 }}>{t('settings.profile_edit')}</button></Link>
          )}
        </section>
      )}

      <section className="card">
        <h2 style={{ marginTop: 0, fontSize: '1rem' }}>{t('settings.account')}</h2>
        {!isAnonymous && email ? (
          <p style={{ margin: 0 }}>{t('settings.account_signed_in', { email })}</p>
        ) : (
          <>
            <p className="muted" style={{ marginTop: 0, fontSize: '0.85rem' }}>{t('settings.account_hint')}</p>
            <label htmlFor="email" className="muted" style={{ fontSize: '0.85rem' }}>{t('settings.account_email')}</label>
            <input id="email" type="email" autoComplete="email" inputMode="email" value={emailText} onChange={(e) => { setEmailText(e.target.value); setNotice(null); }} style={{ margin: '6px 0 10px' }} />
            <div className="row" style={{ justifyContent: 'flex-start' }}>
              <button onClick={keepAccount} disabled={!emailValid || busy}>{t('settings.account_keep')}</button>
              <button className="ghost" onClick={signIn} disabled={!emailValid || busy}>{t('settings.account_signin')}</button>
            </div>
          </>
        )}
        {notice && (
          <p className={notice.error ? 'error' : 'muted'} style={{ fontSize: '0.85rem', marginBottom: 0 }}>
            {t(notice.key, notice.email ? { email: notice.email } : undefined)}
          </p>
        )}
      </section>

      <section className="card">
        <label htmlFor="lang" className="muted" style={{ fontSize: '0.85rem' }}>
          {t('settings.language')}
        </label>
        <select id="lang" className="select" value={locale} onChange={(e) => setLocale(e.target.value as Locale)} style={{ marginTop: 6 }}>
          <option value="en">English</option>
          <option value="uk">Українська</option>
        </select>
      </section>

      <h2>{t('settings.following')}</h2>
      {followed.data?.length ? (
        followed.data.map((a) => (
          <div className="card row" key={a.linkId}>
            <Link to={`/a/${a.ownerId}/day`}>
              <strong>{a.ownerName}</strong>
            </Link>
            <button className="danger" onClick={() => unfollow.mutate(a.linkId)} disabled={unfollow.isPending}>
              {t('settings.unfollow')}
            </button>
          </div>
        ))
      ) : (
        <p className="muted">{t('settings.none')}</p>
      )}

      <p className="muted" style={{ fontSize: '0.85rem' }}>
        {t('settings.privacy')}
      </p>
      <button className="danger" onClick={forget}>
        {t('settings.signout')}
      </button>
      <p className="muted" style={{ fontSize: '0.8rem' }}>
        {t(isAnonymous ? 'settings.signout_hint' : 'settings.signout_hint_email')}
      </p>
    </>
  );
}
