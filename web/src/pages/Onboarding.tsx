import { useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { useSession } from '../lib/session';
import { useEnsureTodaySummary, useLatestWeight, useLogWeight, useProfileDetails, useSaveProfileDetails, type ProfileDetails } from '../lib/tracker';
import { useMyProfile, useSaveMyName } from '../lib/queries';
import { todayEpochDay } from '../lib/dates';
import { setNavDirection, useRouteFirstOpen } from '../lib/motion';
import { ACTIVITY_LEVELS, GOALS, PROFILE_OPTIONS, type ActivityLevel, type Goal, type Sex } from '../domain/model';
import { ageYears, baseTargetKcal, bmr, isAdult, macroTargets, safetyFloorKcal } from '../domain/calories';
import { LargeTitle } from '../components/ui/LargeTitle';
import { Button } from '../components/ui/Button';
import { CardHead } from '../components/ui/Card';
import { Digits } from '../components/ui/Digits';
import { EmptyState } from '../components/ui/EmptyState';
import { Icon } from '../components/ui/Icon';
import { Notice } from '../components/ui/Notice';
import { Ring } from '../components/ui/Ring';
import { SegmentedControl } from '../components/ui/SegmentedControl';
import { Skeleton } from '../components/ui/Skeleton';

/** Entrance delay (ms) for the .a-* classes. */
const delay = (ms: number) => ({ '--d': ms }) as CSSProperties;

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
    return (
      // The title waits too: it is "About you" for a new profile and "Your profile" for an edit,
      // and which one is not known until the profile has loaded.
      <div className="ob">
        <div className="lh ob-skel-title">
          <Skeleton variant="line" width={200} height={34} label={t('loading')} />
        </div>
        <div className="ob-skel">
          <Skeleton variant="block" height={300} />
          <Skeleton variant="block" height={320} />
        </div>
      </div>
    );
  }
  if (!userId) {
    return (
      <div className="ob">
        <LargeTitle title={t('ob.title')} />
        <Notice tone="error" title={t('welcome.failed')} />
      </div>
    );
  }
  if (existing.data?.primary_client === 'phone') {
    return (
      <div className="ob">
        <LargeTitle title={t('ob.edit_title')} back={{ to: '/settings', label: t('nav.settings') }} />
        <div className="card a-rise" style={delay(300)}>
          <EmptyState icon="person" title={t('settings.profile_phone')} titleAs="p" />
        </div>
      </div>
    );
  }
  return <ProfileForm userId={userId} existing={existing.data ?? null} name={myName.data ?? ''} weightKg={latestWeight.data ?? null} />;
}

/** Manual target ranges, as the phone's Settings validates them. */
const RANGES = { protein: [10, 400], fat: [10, 300], carbs: [0, 800] } as const;

/** A grouped section of the form: a sentence-case label above an inset card. */
function Group({ id, label, children, d, className }: { id: string; label?: ReactNode; children: ReactNode; d: number; className?: string }) {
  return (
    <section className={['ob-sec', 'a-rise', className ?? ''].filter(Boolean).join(' ')} style={delay(d)} aria-labelledby={label ? id : undefined}>
      {label && (
        <h2 className="ob-sec-label" id={id}>
          {label}
        </h2>
      )}
      <div className="card ob-group">{children}</div>
    </section>
  );
}

/**
 * One choice of a list (activity level, goal): a native radio stretched over
 * the whole row, so the row is the target and arrow keys move between rows.
 * The radio is named by the whole sentence; the row shows it split at ": "
 * into a title and a description.
 */
function OptionRow({ name, checked, onSelect, label }: { name: string; checked: boolean; onSelect: () => void; label: string }) {
  const { tag } = useI18n();
  const split = label.indexOf(': ');
  const title = split > 0 ? label.slice(0, split) : label;
  const rest = split > 0 ? label.slice(split + 2) : '';
  // The description starts a line of its own, so it starts with a capital ("Exercise 3–5 times a week").
  const detail = rest ? rest.charAt(0).toLocaleUpperCase(tag) + rest.slice(1) : null;
  return (
    <div className={checked ? 'ob-opt is-on' : 'ob-opt'}>
      <input type="radio" name={name} className="ob-opt-input" checked={checked} onChange={onSelect} aria-label={label} />
      <span className="ob-opt-mark" aria-hidden="true">
        <Icon name="check" size="xs" />
      </span>
      <span className="ob-opt-text" aria-hidden="true">
        <span className="ob-opt-title">{title}</span>
        {detail && <span className="ob-opt-detail">{detail}</span>}
      </span>
    </div>
  );
}

function ProfileForm({ userId, existing, name: initialName, weightKg: initialWeight }: {
  userId: string;
  existing: ProfileDetails | null;
  name: string;
  weightKg: number | null;
}) {
  const { t, tag } = useI18n();
  const navigate = useNavigate();
  const first = useRouteFirstOpen();
  const saveDetails = useSaveProfileDetails(userId);
  const saveName = useSaveMyName(userId);
  const logWeight = useLogWeight(userId);
  const ensureSummary = useEnsureTodaySummary(userId);
  const editing = existing !== null;
  const nf = useMemo(() => new Intl.NumberFormat(tag, { maximumFractionDigits: 0 }), [tag]);

  const [name, setName] = useState(initialName);
  const [sex, setSex] = useState<Sex>(existing?.sex ?? 'MALE');
  const [birth, setBirth] = useState(existing?.birth_date ?? '2000-01-01');
  const [height, setHeight] = useState(existing ? String(existing.height_cm) : '');
  const [weight, setWeight] = useState(initialWeight !== null ? String(initialWeight) : '');
  const [level, setLevel] = useState<ActivityLevel>(existing?.activity_level ?? 'SEDENTARY');
  const [goal, setGoal] = useState<Goal>(existing?.goal ?? 'MAINTAIN');
  const [rate, setRate] = useState<number>(existing?.target_kg_per_week || PROFILE_OPTIONS.defaultRateKgPerWeek);
  const [manual, setManual] = useState(existing?.custom_kcal_target != null);
  const [custom, setCustom] = useState({
    kcal: existing?.custom_kcal_target?.toString() ?? '',
    protein: existing?.custom_protein_g?.toString() ?? '',
    fat: existing?.custom_fat_g?.toString() ?? '',
    carbs: existing?.custom_carbs_g?.toString() ?? '',
  });
  const [saveFailed, setSaveFailed] = useState(false);

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
  const saving = saveDetails.isPending || logWeight.isPending || saveName.isPending || ensureSummary.isPending;

  /** Switching manual targets on starts from the computed numbers, like the phone. */
  const toggleManual = (on: boolean) => {
    setManual(on);
    if (on && custom.kcal === '' && macros) {
      setCustom({ kcal: String(macros.kcal), protein: String(macros.proteinG), fat: String(macros.fatG), carbs: String(macros.carbsG) });
    }
  };

  const save = async () => {
    if (!canSave) return;
    setSaveFailed(false);
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
    try {
      if (name.trim() !== initialName) await saveName.mutateAsync(name);
      await saveDetails.mutateAsync(details);
      if (weightKg !== initialWeight) await logWeight.mutateAsync({ weightKg, epochDay: todayEpochDay() });
      const effectiveTarget = customValues?.kcal ?? target;
      if (effectiveTarget !== null) await ensureSummary.mutateAsync(effectiveTarget);
    } catch {
      setSaveFailed(true);
      return;
    }
    setNavDirection('forward');
    navigate('/me/day', { viewTransition: true });
  };

  // The plan reveal (O1): when the inputs first become valid the plan card rises, the target's
  // digits arrive and the ring sweeps round. A saved profile that opens valid builds the same way
  // on the first open of the day, and simply stands there on later visits.
  const valid = macros !== null && bmrKcal !== null;
  const openedValid = useRef(valid);
  const reveal = valid && (first || !openedValid.current);
  // Typed into validity: the card itself rises and the build starts at once. Opened valid: the
  // build waits for the card's own entrance (480 ms) in the page cascade.
  const typedIn = valid && !openedValid.current;
  const base = typedIn ? 0 : 480;

  const cm = t('unit.cm');
  const kg = t('unit.kg');

  const numberField = (key: keyof typeof custom, label: string, ok: boolean, unit: string) => (
    <div className="ob-field" key={key}>
      <label className="field-label" htmlFor={`ob-t-${key}`}>
        {label}
      </label>
      <div className={ok ? 'field' : 'field invalid'}>
        <input
          id={`ob-t-${key}`}
          type="text"
          inputMode="numeric"
          value={custom[key]}
          aria-invalid={!ok || undefined}
          aria-describedby={ok ? undefined : `ob-t-${key}-err`}
          onChange={(e) => setCustom({ ...custom, [key]: e.target.value })}
        />
        <span className="sfx" aria-hidden="true">
          {unit}
        </span>
      </div>
      {!ok && (
        <p className="ob-invalid" id={`ob-t-${key}-err`}>
          {t('settings.target_invalid')}
        </p>
      )}
    </div>
  );

  // Calories from each macro, for the composition bar.
  const share = macros ? { protein: macros.proteinG * 4, fat: macros.fatG * 9, carbs: macros.carbsG * 4 } : null;

  const plan = (
    <section className="card ob-plan" aria-labelledby="ob-plan">
      <CardHead icon="flame" metric="kcal" label={t('ob.summary')} id="ob-plan" />
      {valid && macros && share && bmrKcal !== null ? (
        <div className={['ob-plan-body', reveal ? 'is-build' : '', typedIn ? 'is-reveal' : ''].filter(Boolean).join(' ')} key="plan">
          <div className="ob-plan-hero">
            <span className="ob-plan-ring" style={delay(base + 180)}>
              <Ring value={macros.kcal} target={macros.kcal} size={140} stroke={16} sweep={reveal} delay={base + 200} shine={reveal ? base + 1250 : null} />
              <Icon name="flame" size="lg" className="ember" />
            </span>
            <div className="ob-plan-num">
              <span className="ob-plan-l">{t('ob.target')}</span>
              <span className="ob-plan-v">
                <Digits value={macros.kcal} enter={reveal} delay={base + 120} gradient className="num" />
              </span>
              <span className="ob-plan-u">{t('ob.per_day')}</span>
            </div>
          </div>
          <div className="ob-comp" aria-hidden="true">
            {(['protein', 'fat', 'carbs'] as const).map((m, i) => (
              <i key={m} className={`m-${m}`} style={{ '--w': share[m], '--d': base + 220 + i * 70 } as CSSProperties} />
            ))}
          </div>
          <div className="ob-macros">
            {(
              [
                ['protein', macros.proteinG],
                ['fat', macros.fatG],
                ['carbs', macros.carbsG],
              ] as const
            ).map(([m, grams]) => (
              <div className={`ob-macro m-${m}`} key={m}>
                <span className="ob-macro-l">{t(`ob.${m}`)}</span>
                <span className="ob-macro-v num">
                  <Digits value={grams} />
                  <span className="unit">{t('unit.g')}</span>
                </span>
              </div>
            ))}
          </div>
          <dl className="ob-bmr">
            <dt>{t('ob.bmr')}</dt>
            <dd className="num">
              {nf.format(Math.round(bmrKcal))}
              <span className="unit">{t('unit.kcal')}</span>
            </dd>
          </dl>
          {floored && <p className="footnote ob-floor">{t('ob.floor_note')}</p>}
          <p className="footnote">{t('ob.note')}</p>
        </div>
      ) : (
        // Until height and weight make sense: the plan's shape, empty, so the column never stands bare.
        <div className="ob-plan-body is-empty" key="empty">
          <div className="ob-plan-hero">
            <span className="ob-plan-ring">
              <Ring value={0} target={1} size={140} stroke={16} />
              <Icon name="flame" size="lg" />
            </span>
            <div className="ob-plan-num">
              <span className="ob-plan-l">{t('ob.target')}</span>
              <span className="ob-plan-v ob-plan-dash" aria-hidden="true">
                —
              </span>
              <span className="ob-plan-u">{t('ob.per_day')}</span>
            </div>
          </div>
          <p className="footnote">{t('ob.note')}</p>
        </div>
      )}
    </section>
  );

  return (
    <div className="ob">
      <LargeTitle
        title={editing ? t('ob.edit_title') : t('ob.title')}
        back={editing ? { to: '/settings', label: t('nav.settings') } : undefined}
      />

      <div className="ob-grid">
        <Group id="ob-you" d={300} className="ob-you">
          <div className="ob-field">
            <label className="field-label" htmlFor="ob-name">
              {t('settings.name')}
            </label>
            <div className="field">
              <input id="ob-name" type="text" maxLength={40} autoComplete="given-name" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
          </div>

          <div className="ob-field">
            <span className="field-label" id="ob-sex">
              {t('ob.sex')}
            </span>
            <SegmentedControl<Sex>
              labelledBy="ob-sex"
              options={[
                { value: 'MALE', label: t('ob.male') },
                { value: 'FEMALE', label: t('ob.female') },
              ]}
              value={sex}
              onChange={setSex}
            />
          </div>

          <div className="ob-field">
            <label className="field-label" htmlFor="ob-birth">
              {t('ob.birth')}
            </label>
            <div className={adult ? 'field' : 'field invalid'}>
              <input
                id="ob-birth"
                type="date"
                value={birth}
                aria-describedby="ob-birth-hint"
                aria-invalid={!adult || undefined}
                onChange={(e) => setBirth(e.target.value)}
              />
            </div>
            <p className="footnote ob-hint" id="ob-birth-hint">
              {t('ob.birth_hint')}
            </p>
            {!adult && <Notice tone="warning" title={t('ob.underage')} />}
          </div>

          <div className="ob-pair">
            <div className="ob-field">
              <label className="field-label" htmlFor="ob-height">
                {t('ob.height')}
              </label>
              <div className="field">
                <input id="ob-height" type="text" inputMode="numeric" value={height} onChange={(e) => setHeight(e.target.value)} />
                <span className="sfx" aria-hidden="true">
                  {cm}
                </span>
              </div>
            </div>
            <div className="ob-field">
              <label className="field-label" htmlFor="ob-weight">
                {t('ob.weight')}
              </label>
              <div className="field">
                <input id="ob-weight" type="text" inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} />
                <span className="sfx" aria-hidden="true">
                  {kg}
                </span>
              </div>
            </div>
          </div>
        </Group>

        <Group id="ob-activity" label={t('ob.activity')} d={360} className="ob-list">
          <div role="radiogroup" aria-labelledby="ob-activity">
            {ACTIVITY_LEVELS.map((l) => (
              <OptionRow key={l} name="level" checked={level === l} onSelect={() => setLevel(l)} label={t(`ob.act.${l}`)} />
            ))}
          </div>
        </Group>

        <Group id="ob-goal" label={t('ob.goal')} d={420} className="ob-list">
          <div role="radiogroup" aria-labelledby="ob-goal">
            {GOALS.map((g) => (
              <OptionRow key={g} name="goal" checked={goal === g} onSelect={() => setGoal(g)} label={t(`ob.goal.${g}`)} />
            ))}
          </div>
          {goal !== 'MAINTAIN' && (
            <div className="ob-rate">
              <span className="field-label" id="ob-rate">
                {t('ob.rate')}
              </span>
              <SegmentedControl<number>
                labelledBy="ob-rate"
                options={PROFILE_OPTIONS.rateOptions.map((r) => ({ value: r, label: String(r) }))}
                value={rate}
                onChange={setRate}
              />
            </div>
          )}
        </Group>

        <div className="ob-plan-slot a-rise" style={delay(480)}>
          {plan}
        </div>

        <Group id="ob-targets" label={t('settings.targets')} d={540} className="ob-targets">
          <label className="ob-switch">
            <span>{t('settings.targets_manual')}</span>
            <input type="checkbox" role="switch" className="switch" checked={manual} onChange={(e) => toggleManual(e.target.checked)} />
          </label>
          {manual && customValues && (
            <div className="ob-target-grid">
              {numberField('kcal', t('settings.target_kcal'), customValues.kcal !== null, t('unit.kcal'))}
              {numberField('protein', t('settings.target_protein'), customValues.protein !== null, t('unit.g'))}
              {numberField('fat', t('settings.target_fat'), customValues.fat !== null, t('unit.g'))}
              {numberField('carbs', t('settings.target_carbs'), customValues.carbs !== null, t('unit.g'))}
            </div>
          )}
        </Group>

        <div className="ob-save a-rise" style={delay(600)}>
          {saveFailed && <Notice tone="error" title={t('welcome.failed')} />}
          <Button variant="ink" size="lg" onClick={save} disabled={!canSave || saving}>
            {editing ? t('ob.update') : t('ob.save')}
          </Button>
        </div>
      </div>
    </div>
  );
}
