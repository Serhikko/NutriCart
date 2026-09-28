import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n, type Locale } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useFollowed, useMyProfile, useSaveMyName, useUnfollow } from '../lib/queries';
import { supabase } from '../lib/supabase';

/** Name, language, the accounts followed, and the way out. */
export function Settings() {
  const { t, locale, setLocale } = useI18n();
  const { userId } = useSession();
  const profile = useMyProfile(userId);
  const saveName = useSaveMyName(userId);
  const followed = useFollowed(userId);
  const unfollow = useUnfollow(userId);
  const [name, setName] = useState('');

  useEffect(() => {
    if (profile.data !== undefined) setName(profile.data);
  }, [profile.data]);

  const forget = async () => {
    await supabase.auth.signOut();
    window.location.href = '/';
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
        {t('settings.signout_hint')}
      </p>
    </>
  );
}
