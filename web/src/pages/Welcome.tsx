import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useFollowed, useMyProfile, useRedeemCode, useSaveMyName } from '../lib/queries';
import { isWellFormedPairingCode, normalizePairingCode } from '../lib/pairing';

/** Code entry, and the list of accounts already followed. */
export function Welcome() {
  const { t } = useI18n();
  const { userId, loading, error } = useSession();
  const followed = useFollowed(userId);
  const profile = useMyProfile(userId);
  const saveName = useSaveMyName(userId);
  const redeem = useRedeemCode(userId);
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const normalized = normalizePairingCode(code);
    if (!isWellFormedPairingCode(normalized)) return;
    const chosen = name.trim() || profile.data || '';
    if (chosen && chosen !== profile.data) await saveName.mutateAsync(chosen);
    redeem.mutate(normalized, { onSuccess: (account) => navigate(`/a/${account.ownerId}/day`) });
  };

  if (error === 'not_configured') return <p className="error">{t('welcome.not_configured')}</p>;

  return (
    <>
      <h1>{t('welcome.title')}</h1>
      <p className="muted">{t('welcome.intro')}</p>

      <form className="card" onSubmit={onSubmit}>
        <label htmlFor="code" className="muted" style={{ fontSize: '0.85rem' }}>
          {t('welcome.code')}
        </label>
        <input
          id="code"
          className="code"
          type="text"
          inputMode="text"
          autoComplete="off"
          autoCapitalize="characters"
          maxLength={8}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          style={{ margin: '6px 0 12px' }}
        />
        <label htmlFor="name" className="muted" style={{ fontSize: '0.85rem' }}>
          {t('welcome.name')}
        </label>
        <input
          id="name"
          type="text"
          maxLength={40}
          value={name}
          placeholder={profile.data || ''}
          onChange={(e) => setName(e.target.value)}
          style={{ margin: '6px 0 12px' }}
        />
        <div className="row">
          <span className="error" style={{ fontSize: '0.85rem' }}>
            {redeem.isError ? t(`welcome.${redeem.error}`) : ''}
          </span>
          <button type="submit" disabled={loading || redeem.isPending || !isWellFormedPairingCode(normalizePairingCode(code))}>
            {redeem.isPending ? t('welcome.connecting') : t('welcome.connect')}
          </button>
        </div>
      </form>

      {followed.data && followed.data.length > 0 && (
        <>
          <h2>{t('welcome.linked')}</h2>
          {followed.data.map((a) => (
            <div className="card row" key={a.linkId}>
              <strong>{a.ownerName}</strong>
              <Link to={`/a/${a.ownerId}/day`}>
                <button className="ghost">{t('welcome.open')}</button>
              </Link>
            </div>
          ))}
        </>
      )}
    </>
  );
}
