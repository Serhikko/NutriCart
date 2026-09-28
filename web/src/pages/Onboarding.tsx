import { useState, type JSX } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useEnsureTodaySummary, useLatestWeight, useLogWeight, useProfileDetails, useSaveProfileDetails, type ProfileDetails } from '../lib/tracker';
import { useMyProfile, useSaveMyName } from '../lib/queries';
import { todayEpochDay } from '../lib/dates';
import { ACTIVITY_LEVELS, GOALS, PROFILE_OPTIONS, type ActivityLevel, type Goal, type Sex } from '../domain/model';
import { ageYears, baseTargetKcal, bmr, isAdult, macroTargets, safetyFloorKcal } from '../domain/calories';

/**
 * The phone's onboarding questionnaire on one scrolling page, with the same
 * math and the same summary card; the same page edits the profile later
 * (Settings → Your profile), including the manual daily targets the phone
 * keeps in its Settings. Saving writes profile_details, today's weight, the
 * display name and today's summary.
 *
 * The form is rendered only once the stored values have loaded, so an edit
 * starts from what is saved rather than from the defaults.
 */
export function Onboarding() {
  const { t } = useI18n();
  const { userId, loading: sessionLoading } = useSession();
  const existing = useProfileDetails(userId);
  const myName = useMyProfile(userId);
  const latestWeight = useLatestWeight(userId);

  if (sessionLoading || existing.isLoading || myName.isLoading || latestWeight.isLoading) {
    return <p className="muted">{t('loading')}</p>;
  }
  if (!userId) return <p className="error">{t('welcome.failed')}</p>;
  if (existing.data?.primary_client === 'phone') {
    return (
      <>
        <h1>{t('ob.edit_title')}</h1>
        <p className="muted">{t('settings.profile_phone')}</p>
      </>
    );
  }
  return <ProfileForm userId={userId} existing={existing.data ?? null} name={myName.data ?? ''} weightKg={latestWeight.data ?? null} />;
}

/** Manual target ranges, as the phone's Settings validates them. */
const RANGES = { protein: [10, 400], fat: [10, 300], carbs: [0, 800] } as const;

function ProfileForm({ userId, existing, name: initialName, weightKg: initialWeight }: {
  userId: string;
  existing: ProfileDetails | null;
  name: string;
  weightKg: number | null;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const saveDetails = useSaveProfileDetails(userId);
  const saveName = useSaveMyName(userId);
  const logWeight = useLogWeight(userId);
  const ensureSummary = useEnsureTodaySummary(userId);
  const editing = existing !== null;

  const [name, setName] = useState(initialName);
  const [sex, setSex] = useState<Sex>(existing?.sex ?? 'MALE');
  const [birth, setBirth] = useState(existing?.birth_date ?? '2000-01-01');
  const [height, setHeight] = useState(existing ? String(existing.height_cm) : '');
  const [weight, setWeight] = useState(initialWeight !== null ? String(initialWeight) : '');
  const [level, setLevel] = useState<ActivityLevel>(existing?.activity_level ?? 'SEDENTARY');
  const [goal, setGoal] = useState<Goal>(existing?.goal ?? 'MAINTAIN');
  const [rate, setRate] = useState(existing?.target_kg_per_week || PROFILE_OPTIONS.defaultRateKgPerWeek);
  const [manual, setManual] = useState(existing?.custom_kcal_target != null);
  const [custom, setCustom] = useState({
    kcal: existing?.custom_kcal_target?.toString() ?? '',
    protein: existing?.custom_protein_g?.toString() ?? '',
    fat: existing?.custom_fat_g?.toString() ?? '',
    carbs: existing?.custom_carbs_g?.toString() ?? '',
  });

  const today = new Date();
  const [by, bm, bd] = birth.split('-').map(Number);
  const birthDate = new Date(by, (bm || 1) - 1, bd || 1);
  const adult = Number.isFinite(by) && isAdult(birthDate, today);
  const heightCm = Number(height);
  const weightKg = Number(weight.replace(',', '.'));
  const heightOk = heightCm >= PROFILE_OPTIONS.heightCm.min && heightCm <= PROFILE_OPTIONS.heightCm.max;
  const weightOk = weightKg >= PROFILE_OPTIONS.weightKg.min && weightKg <= PROFILE_OPTIONS.weightKg.max;

  const age = Number.isFinite(by) ? ageYears(birthDate, today) : 0;
  const bmrKcal = heightOk && weightOk ? bmr(sex, weightKg, heightCm, age) : null;
  const target = heightOk && weightOk ? baseTargetKcal(sex, weightKg, heightCm, age, level, goal, goal === 'MAINTAIN' ? 0 : rate) : null;
  const floored = target !== null && target === safetyFloorKcal(sex);
  const macros = target !== null ? macroTargets(target, weightKg) : null;

  // Manual targets: all four within the phone's ranges, or the switch is off.
  const inRange = (text: string, [min, max]: readonly [number, number]) => {
    const n = Number(text);
    return Number.isInteger(n) && n >= min && n <= max ? n : null;
  };
  const customValues = manual
    ? {
        kcal: inRange(custom.kcal, [Math.round(safetyFloorKcal(sex)), 6000]),
        protein: inRange(custom.protein, RANGES.protein),
        fat: inRange(custom.fat, RANGES.fat),
        carbs: inRange(custom.carbs, RANGES.carbs),
      }
    : null;
  const customOk = !customValues || Object.values(customValues).every((v) => v !== null);
  const canSave = adult && heightOk && weightOk && name.trim().length > 0 && (goal === 'MAINTAIN' || rate > 0) && customOk;

  /** Switching manual targets on starts from the computed numbers, like the phone. */
  const toggleManual = (on: boolean) => {
    setManual(on);
    if (on && custom.kcal === '' && macros) {
      setCustom({ kcal: String(macros.kcal), protein: String(macros.proteinG), fat: String(macros.fatG), carbs: String(macros.carbsG) });
    }
  };

  const save = async () => {
    if (!canSave) return;
    const details: ProfileDetails = {
      sex,
      birth_date: birth,
      height_cm: heightCm,
      activity_level: level,
      goal,
      target_kg_per_week: goal === 'MAINTAIN' ? 0 : rate,
      custom_kcal_target: customValues?.kcal ?? null,
      custom_protein_g: customValues?.protein ?? null,
      custom_fat_g: customValues?.fat ?? null,
      custom_carbs_g: customValues?.carbs ?? null,
      primary_client: 'web',
    };
    if (name.trim() !== initialName) await saveName.mutateAsync(name);
    await saveDetails.mutateAsync(details);
    if (weightKg !== initialWeight) await logWeight.mutateAsync({ weightKg, epochDay: todayEpochDay() });
    const effectiveTarget = customValues?.kcal ?? target;
    if (effectiveTarget !== null) await ensureSummary.mutateAsync(effectiveTarget);
    navigate('/me/day');
  };

  const field = (label: string, input: JSX.Element) => (
    <label className="stat" style={{ marginBottom: 12 }}>
      <span className="muted" style={{ fontSize: '0.85rem' }}>{label}</span>
      {input}
    </label>
  );
  const numberField = (key: keyof typeof custom, label: string, ok: boolean) => (
    <label className="stat" style={{ marginBottom: 12 }} key={key}>
      <span className="muted" style={{ fontSize: '0.85rem' }}>{label}</span>
      <input type="text" inputMode="numeric" value={custom[key]} onChange={(e) => setCustom({ ...custom, [key]: e.target.value })} />
      {!ok && <span className="error" style={{ fontSize: '0.8rem' }}>{t('settings.target_invalid')}</span>}
    </label>
  );

  return (
    <>
      <h1>{editing ? t('ob.edit_title') : t('ob.title')}</h1>
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

      <section className="card">
        <label className="row" style={{ cursor: 'pointer' }}>
          <span>{t('settings.targets_manual')}</span>
          <input type="checkbox" checked={manual} onChange={(e) => toggleManual(e.target.checked)} />
        </label>
        {manual && customValues && (
          <div style={{ marginTop: 10 }}>
            {numberField('kcal', t('settings.target_kcal'), customValues.kcal !== null)}
            {numberField('protein', t('settings.target_protein'), customValues.protein !== null)}
            {numberField('fat', t('settings.target_fat'), customValues.fat !== null)}
            {numberField('carbs', t('settings.target_carbs'), customValues.carbs !== null)}
          </div>
        )}
      </section>

      <button onClick={save} disabled={!canSave || saveDetails.isPending || logWeight.isPending} style={{ width: '100%' }}>
        {editing ? t('ob.update') : t('ob.save')}
      </button>
    </>
  );
}
