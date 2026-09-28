import { useState, type JSX } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useEnsureTodaySummary, useLogWeight, useProfileDetails, useSaveProfileDetails, type ProfileDetails } from '../lib/tracker';
import { useMyProfile, useSaveMyName } from '../lib/queries';
import { todayEpochDay } from '../lib/dates';
import { ACTIVITY_LEVELS, GOALS, PROFILE_OPTIONS, type ActivityLevel, type Goal, type Sex } from '../domain/model';
import { ageYears, baseTargetKcal, bmr, isAdult, macroTargets, safetyFloorKcal } from '../domain/calories';

/**
 * The phone's onboarding questionnaire, on one scrolling page, with the same
 * math and the same summary card. Saving writes profile_details, the first
 * weight entry, the display name and today's summary.
 */
export function Onboarding() {
  const { t } = useI18n();
  const { userId } = useSession();
  const navigate = useNavigate();
  const existing = useProfileDetails(userId);
  const myName = useMyProfile(userId);
  const saveDetails = useSaveProfileDetails(userId);
  const saveName = useSaveMyName(userId);
  const logWeight = useLogWeight(userId);
  const ensureSummary = useEnsureTodaySummary(userId);

  const [name, setName] = useState(myName.data ?? '');
  const [sex, setSex] = useState<Sex>(existing.data?.sex ?? 'MALE');
  const [birth, setBirth] = useState(existing.data?.birth_date ?? '2000-01-01');
  const [height, setHeight] = useState(existing.data ? String(existing.data.height_cm) : '');
  const [weight, setWeight] = useState('');
  const [level, setLevel] = useState<ActivityLevel>(existing.data?.activity_level ?? 'SEDENTARY');
  const [goal, setGoal] = useState<Goal>(existing.data?.goal ?? 'MAINTAIN');
  const [rate, setRate] = useState(existing.data?.target_kg_per_week ?? PROFILE_OPTIONS.defaultRateKgPerWeek);

  const today = new Date();
  const [by, bm, bd] = birth.split('-').map(Number);
  const birthDate = new Date(by, (bm || 1) - 1, bd || 1);
  const adult = Number.isFinite(by) && isAdult(birthDate, today);
  const heightCm = Number(height);
  const weightKg = Number(weight.replace(',', '.'));
  const heightOk = heightCm >= PROFILE_OPTIONS.heightCm.min && heightCm <= PROFILE_OPTIONS.heightCm.max;
  const weightOk = weightKg >= PROFILE_OPTIONS.weightKg.min && weightKg <= PROFILE_OPTIONS.weightKg.max;
  const canSave = adult && heightOk && weightOk && name.trim().length > 0 && (goal === 'MAINTAIN' || rate > 0);

  const age = Number.isFinite(by) ? ageYears(birthDate, today) : 0;
  const bmrKcal = heightOk && weightOk ? bmr(sex, weightKg, heightCm, age) : null;
  const target = heightOk && weightOk ? baseTargetKcal(sex, weightKg, heightCm, age, level, goal, goal === 'MAINTAIN' ? 0 : rate) : null;
  const floored = target !== null && target === safetyFloorKcal(sex);
  const macros = target !== null ? macroTargets(target, weightKg) : null;

  const save = async () => {
    if (!canSave || !userId) return;
    const details: ProfileDetails = {
      sex,
      birth_date: birth,
      height_cm: heightCm,
      activity_level: level,
      goal,
      target_kg_per_week: goal === 'MAINTAIN' ? 0 : rate,
      custom_kcal_target: existing.data?.custom_kcal_target ?? null,
      custom_protein_g: existing.data?.custom_protein_g ?? null,
      custom_fat_g: existing.data?.custom_fat_g ?? null,
      custom_carbs_g: existing.data?.custom_carbs_g ?? null,
      primary_client: 'web',
    };
    await saveName.mutateAsync(name);
    await saveDetails.mutateAsync(details);
    await logWeight.mutateAsync({ weightKg, epochDay: todayEpochDay() });
    if (target !== null) await ensureSummary.mutateAsync(existing.data?.custom_kcal_target ?? target);
    navigate('/me/day');
  };

  const field = (label: string, input: JSX.Element) => (
    <label className="stat" style={{ marginBottom: 12 }}>
      <span className="muted" style={{ fontSize: '0.85rem' }}>{label}</span>
      {input}
    </label>
  );

  return (
    <>
      <h1>{t('ob.title')}</h1>
      <section className="card">
        {field(t('settings.name'), <input type="text" maxLength={40} value={name} onChange={(e) => setName(e.target.value)} />)}
        <span className="muted" style={{ fontSize: '0.85rem' }}>{t('ob.sex')}</span>
        <div className="row" style={{ justifyContent: 'flex-start', margin: '6px 0 12px' }}>
          <button className={sex === 'MALE' ? '' : 'ghost'} onClick={() => setSex('MALE')}>{t('ob.male')}</button>
          <button className={sex === 'FEMALE' ? '' : 'ghost'} onClick={() => setSex('FEMALE')}>{t('ob.female')}</button>
        </div>
        {field(t('ob.birth'), <input type="date" value={birth} onChange={(e) => setBirth(e.target.value)} className="select" />)}
        {!adult && <p className="error" style={{ marginTop: -6 }}>{t('ob.underage')}</p>}
        {field(t('ob.height'), <input type="text" inputMode="numeric" value={height} onChange={(e) => setHeight(e.target.value)} />)}
        {field(t('ob.weight'), <input type="text" inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} />)}
      </section>

      <section className="card">
        <span className="muted" style={{ fontSize: '0.85rem' }}>{t('ob.activity')}</span>
        {ACTIVITY_LEVELS.map((l) => (
          <label key={l} className="item" style={{ cursor: 'pointer' }}>
            <input type="radio" name="level" checked={level === l} onChange={() => setLevel(l)} />
            <span className="name" style={{ marginLeft: 8 }}>{t(`ob.act.${l}`)}</span>
          </label>
        ))}
      </section>

      <section className="card">
        <span className="muted" style={{ fontSize: '0.85rem' }}>{t('ob.goal')}</span>
        {GOALS.map((g) => (
          <label key={g} className="item" style={{ cursor: 'pointer' }}>
            <input type="radio" name="goal" checked={goal === g} onChange={() => setGoal(g)} />
            <span className="name" style={{ marginLeft: 8 }}>{t(`ob.goal.${g}`)}</span>
          </label>
        ))}
        {goal !== 'MAINTAIN' && (
          <>
            <span className="muted" style={{ fontSize: '0.85rem', display: 'block', marginTop: 10 }}>{t('ob.rate')}</span>
            <div className="row" style={{ justifyContent: 'flex-start', marginTop: 6 }}>
              {PROFILE_OPTIONS.rateOptions.map((r) => (
                <button key={r} className={rate === r ? '' : 'ghost'} onClick={() => setRate(r)}>{r}</button>
              ))}
            </div>
          </>
        )}
      </section>

      {bmrKcal !== null && target !== null && macros && (
        <section className="card">
          <h2 style={{ marginTop: 0 }}>{t('ob.summary')}</h2>
          <div className="item"><span className="name muted">{t('ob.bmr')}</span><span>{Math.round(bmrKcal)} {t('unit.kcal')}</span></div>
          <div className="item"><span className="name muted">{t('ob.target')}</span><strong>{macros.kcal} {t('unit.kcal')}</strong></div>
          <div className="item"><span className="name muted">{t('ob.protein')}</span><span>{macros.proteinG} {t('unit.g')}</span></div>
          <div className="item"><span className="name muted">{t('ob.fat')}</span><span>{macros.fatG} {t('unit.g')}</span></div>
          <div className="item"><span className="name muted">{t('ob.carbs')}</span><span>{macros.carbsG} {t('unit.g')}</span></div>
          {floored && <p className="muted" style={{ fontSize: '0.85rem' }}>{t('ob.floor_note')}</p>}
          <p className="muted" style={{ fontSize: '0.8rem' }}>{t('ob.note')}</p>
        </section>
      )}

      <button onClick={save} disabled={!canSave || saveDetails.isPending || logWeight.isPending} style={{ width: '100%' }}>
        {t('ob.save')}
      </button>
    </>
  );
}
