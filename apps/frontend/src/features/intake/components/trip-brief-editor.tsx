'use client';

import { Alert, Card, Checkbox, DatePicker, Form, Input, InputNumber, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';
import type { FocusEvent, ReactNode } from 'react';
import { ApiError } from '@/lib/api/api-error';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import {
  buildUpdateTripBriefRequest,
  CURRENCIES,
  DATE_FLEXIBILITIES,
  mergeBriefFormValues,
  TRAVEL_INTERESTS,
  TRAVEL_PACES,
  type TripBriefField,
  type TripBriefFormValues,
} from '../lib/brief-form';
import { enumLabelKey } from '../lib/clarification';
import type { DateRange, TripBrief } from '../types';
import { useTripBrief, useUpdateTripBrief } from '../hooks/use-trip-brief';
import { ClarificationPanel } from './clarification-panel';
import { ConflictNotice } from './conflict-notice';
import { SaveStatus, type SaveState } from './save-status';

export interface TripBriefEditorProps {
  tripId: string;
}

const FIELD_NAMES = [
  'destinations',
  'dates',
  'date_flexibility',
  'departure_city',
  'budget',
  'party',
  'interests',
  'pace',
] as const satisfies readonly TripBriefField[];

/**
 * Structured C1 editor with debounced full-resource saves. Conflict handling follows ADR 008:
 * refetch, merge only dirty/focused-safe fields onto server truth, and show a non-blocking notice.
 */
export function TripBriefEditor({ tripId }: TripBriefEditorProps) {
  const t = useTranslations('trip_brief');
  const [form] = Form.useForm<TripBriefFormValues>();
  const brief = useTripBrief(tripId);
  const saveBrief = useUpdateTripBrief();
  const latestBriefRef = useRef<TripBrief | null>(null);
  const dirtyFieldsRef = useRef(new Set<TripBriefField>());
  const fieldClocksRef = useRef(newFieldClocks());
  const focusedFieldRef = useRef<TripBriefField | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [conflictVersion, setConflictVersion] = useState<number | null>(null);

  useEffect(() => {
    if (!brief.data) {
      return;
    }
    latestBriefRef.current = brief.data;
    const draft = form.getFieldsValue(true) as TripBriefFormValues;
    const values = mergeBriefFormValues({
      serverBrief: brief.data,
      draft,
      keepFields: dirtyFieldsRef.current,
      focusedField: focusedFieldRef.current,
    });
    form.setFieldsValue(values);
  }, [brief.data, form]);

  if (brief.isPending) {
    return <LoadingState label={t('states.loading')} rows={8} />;
  }

  if (brief.isError) {
    return <ErrorAlert error={brief.error} onRetry={() => void brief.refetch()} />;
  }

  const archived = brief.data.status === 'ARCHIVED';

  function onValuesChange(changed: Partial<TripBriefFormValues>, values: TripBriefFormValues) {
    markDirty(changed);
    setConflictVersion(null);
    scheduleSave(values, archived);
  }

  return (
    <div className="flex max-w-[720px] flex-col gap-4">
      {archived ? (
        <Alert showIcon type="info" message={t('archived.notice')} description={t('archived.read_only')} />
      ) : null}
      <ConflictNotice visible={conflictVersion !== null} currentVersion={conflictVersion} />
      <Card title={t('detail.form_title')} extra={<SaveStatus state={saveState} />} className="border-border-subtle">
        <Form
          form={form}
          layout="vertical"
          disabled={archived}
          onValuesChange={onValuesChange}
          onFocusCapture={rememberFocusedField}
          onBlurCapture={clearFocusedField}
        >
          <BriefFields t={t} />
        </Form>
      </Card>
      <ClarificationPanel tripId={tripId} brief={brief.data} disabled={archived} />
    </div>
  );

  function scheduleSave(values: TripBriefFormValues, disabled: boolean) {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    if (disabled) {
      return;
    }
    timerRef.current = setTimeout(() => {
      void save(values);
    }, 600);
  }

  async function save(values: TripBriefFormValues) {
    const latest = latestBriefRef.current;
    if (!latest) {
      return;
    }
    const clocks = { ...fieldClocksRef.current };
    setSaveState('saving');
    try {
      const saved = await saveBrief.mutateAsync({
        tripId,
        request: buildUpdateTripBriefRequest(values, latest.version),
      });
      latestBriefRef.current = saved;
      clearSyncedFields(clocks);
      setSaveState('saved');
    } catch (error) {
      await handleSaveError(error);
    }
  }

  async function handleSaveError(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
      const refetched = await brief.refetch();
      if (refetched.data) {
        applyConflictMerge(refetched.data, error.currentVersion);
        return;
      }
    }
    setSaveState('error');
  }

  function applyConflictMerge(serverBrief: TripBrief, version: number | null) {
    latestBriefRef.current = serverBrief;
    const draft = form.getFieldsValue(true) as TripBriefFormValues;
    form.setFieldsValue(
      mergeBriefFormValues({
        serverBrief,
        draft,
        keepFields: dirtyFieldsRef.current,
        focusedField: focusedFieldRef.current,
      }),
    );
    setConflictVersion(version ?? serverBrief.version);
    setSaveState('idle');
  }

  function markDirty(changed: Partial<TripBriefFormValues>) {
    for (const key of Object.keys(changed)) {
      if (isTripBriefField(key)) {
        dirtyFieldsRef.current.add(key);
        fieldClocksRef.current[key] += 1;
      }
    }
  }

  function clearSyncedFields(clocks: FieldClocks) {
    for (const field of FIELD_NAMES) {
      if (fieldClocksRef.current[field] === clocks[field]) {
        dirtyFieldsRef.current.delete(field);
      }
    }
  }

  function rememberFocusedField(event: FocusEvent<HTMLFormElement>) {
    focusedFieldRef.current = fieldFromTarget(event.target);
  }

  function clearFocusedField() {
    focusedFieldRef.current = null;
  }
}

type Translate = ReturnType<typeof useTranslations<'trip_brief'>>;
type FieldClocks = Record<TripBriefField, number>;

function BriefFields({ t }: { t: Translate }) {
  return (
    <>
      <FieldFrame field="destinations">
        <Form.Item name="destinations" label={t('field.destinations')} help={t('field.destinations_help')}>
          <Select mode="tags" tokenSeparators={[',']} placeholder={t('field.destinations_placeholder')} />
        </Form.Item>
      </FieldFrame>
      <div className="grid gap-4 md:grid-cols-2">
        <FieldFrame field="dates">
          <Form.Item
            name="dates"
            label={t('field.dates')}
            getValueProps={dateRangeValueProps}
            normalize={normalizeDateRange}
          >
            <DatePicker.RangePicker className="w-full" />
          </Form.Item>
        </FieldFrame>
        <FieldFrame field="date_flexibility">
          <Form.Item name="date_flexibility" label={t('field.date_flexibility')}>
            <Select allowClear options={DATE_FLEXIBILITIES.map((value) => enumOption('date_flexibility', value, t))} />
          </Form.Item>
        </FieldFrame>
      </div>
      <FieldFrame field="departure_city">
        <Form.Item name="departure_city" label={t('field.departure_city')}>
          <Input placeholder={t('field.departure_city_placeholder')} />
        </Form.Item>
      </FieldFrame>
      <BudgetFields t={t} />
      <PartyFields t={t} />
      <FieldFrame field="interests">
        <Form.Item name="interests" label={t('field.interests')}>
          <Checkbox.Group options={TRAVEL_INTERESTS.map((value) => enumOption('travel_interest', value, t))} />
        </Form.Item>
      </FieldFrame>
      <FieldFrame field="pace">
        <Form.Item name="pace" label={t('field.pace')}>
          <Select allowClear options={TRAVEL_PACES.map((value) => enumOption('travel_pace', value, t))} />
        </Form.Item>
      </FieldFrame>
    </>
  );
}

function BudgetFields({ t }: { t: Translate }) {
  return (
    <FieldFrame field="budget">
      <Form.Item label={t('field.budget')}>
        <div className="grid grid-cols-[1fr_112px] gap-2">
          <Form.Item name={['budget', 'amount']} noStyle>
            <InputNumber stringMode min="0" className="w-full" placeholder={t('field.budget_placeholder')} />
          </Form.Item>
          <Form.Item name={['budget', 'currency']} noStyle>
            <Select options={CURRENCIES.map((currency) => ({ value: currency, label: currency }))} />
          </Form.Item>
        </div>
      </Form.Item>
    </FieldFrame>
  );
}

function PartyFields({ t }: { t: Translate }) {
  return (
    <FieldFrame field="party">
      <Form.Item label={t('field.party')}>
        <div className="grid gap-2 md:grid-cols-2">
          <Form.Item name={['party', 'adults']} label={t('field.party_adults')}>
            <InputNumber min={1} max={20} className="w-full" />
          </Form.Item>
          <Form.Item name={['party', 'children']} label={t('field.party_children')}>
            <InputNumber min={0} max={20} className="w-full" />
          </Form.Item>
        </div>
      </Form.Item>
    </FieldFrame>
  );
}

function FieldFrame({ field, children }: FieldFrameProps) {
  return <div data-brief-field={field}>{children}</div>;
}

interface FieldFrameProps {
  field: TripBriefField;
  children: ReactNode;
}

function enumOption(group: string, value: string, t: Translate) {
  return { value, label: t(enumLabelKey(group, value)) };
}

function dateRangeValueProps(value: DateRange | null | undefined) {
  return { value: value ? [dayjs(value.start_date), dayjs(value.end_date)] : null };
}

function normalizeDateRange(value: unknown): DateRange | null {
  if (!Array.isArray(value) || value.length !== 2) {
    return null;
  }
  const [start, end] = value;
  if (!dayjs.isDayjs(start) || !dayjs.isDayjs(end)) {
    return null;
  }
  return { start_date: formatDate(start), end_date: formatDate(end) };
}

function formatDate(value: Dayjs): string {
  return value.format('YYYY-MM-DD');
}

function fieldFromTarget(target: EventTarget): TripBriefField | null {
  const element = target instanceof Element ? target : null;
  const container = element?.closest('[data-brief-field]');
  const field = container?.getAttribute('data-brief-field') ?? '';
  return isTripBriefField(field) ? field : null;
}

function isTripBriefField(value: string): value is TripBriefField {
  return (FIELD_NAMES as readonly string[]).includes(value);
}

function newFieldClocks(): FieldClocks {
  return Object.fromEntries(FIELD_NAMES.map((field) => [field, 0])) as FieldClocks;
}
