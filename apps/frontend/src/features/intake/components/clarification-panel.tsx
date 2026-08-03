'use client';

import { Button, Card, DatePicker, Form, Input, InputNumber, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslations } from 'next-intl';
import { CURRENCIES } from '../lib/brief-form';
import { buildClarificationAnswers, enumLabelKey, promptKey, type ClarificationFormValues } from '../lib/clarification';
import type { ClarificationQuestion, DateRange, TripBrief } from '../types';
import { useAnswerTripBriefClarification } from '../hooks/use-trip-brief';

export interface ClarificationPanelProps {
  tripId: string;
  brief: TripBrief;
  disabled?: boolean;
}

/**
 * Typed clarification is the visible half of "never silently guess" (UC-C1-04). Each control is
 * chosen from the backend question type and submitted through the ADR 008 action endpoint.
 */
export function ClarificationPanel({ tripId, brief, disabled = false }: ClarificationPanelProps) {
  const t = useTranslations('trip_brief');
  const [form] = Form.useForm<ClarificationFormValues>();
  const answer = useAnswerTripBriefClarification();
  const questions = brief.clarification.questions;

  if (questions.length === 0) {
    return null;
  }

  async function submit(values: ClarificationFormValues) {
    await answer.mutateAsync({
      tripId,
      request: {
        expected_version: brief.version,
        answers: buildClarificationAnswers(questions, values),
      },
    });
    form.resetFields();
  }

  return (
    <Card title={t('clarification.title')}>
      <Form form={form} layout="vertical" onFinish={submit} disabled={disabled || answer.isPending}>
        <div className="grid gap-4 md:grid-cols-2">
          {questions.map((question) => (
            <QuestionField key={question.id} question={question} t={t} />
          ))}
        </div>
        <Button type="primary" htmlType="submit" loading={answer.isPending} className="mt-2 min-h-control">
          {t('clarification.submit')}
        </Button>
      </Form>
    </Card>
  );
}

type Translate = ReturnType<typeof useTranslations<'trip_brief'>>;

interface QuestionFieldProps {
  question: ClarificationQuestion;
  t: Translate;
}

function QuestionField({ question, t }: QuestionFieldProps) {
  const label = t(promptKey(question.prompt_key));
  const rules = question.required ? [{ required: true, message: t('clarification.required') }] : [];

  if (question.type === 'MONEY') {
    return <MoneyQuestion question={question} label={label} rules={rules} />;
  }

  return (
    <Form.Item
      name={question.id}
      label={label}
      rules={rules}
      getValueProps={question.type === 'DATE_RANGE' ? dateRangeValueProps : undefined}
      normalize={question.type === 'DATE_RANGE' ? normalizeDateRange : undefined}
    >
      {controlForQuestion(question, t)}
    </Form.Item>
  );
}

function MoneyQuestion({ question, label, rules }: MoneyQuestionProps) {
  return (
    <Form.Item label={label} required={question.required}>
      <div className="grid grid-cols-[1fr_112px] gap-2">
        <Form.Item name={[question.id, 'amount']} noStyle rules={rules}>
          <InputNumber stringMode min="0" className="w-full" />
        </Form.Item>
        <Form.Item name={[question.id, 'currency']} noStyle initialValue="MYR">
          <Select options={CURRENCIES.map((currency) => ({ value: currency, label: currency }))} />
        </Form.Item>
      </div>
    </Form.Item>
  );
}

interface MoneyQuestionProps {
  question: ClarificationQuestion;
  label: string;
  rules: { required: boolean; message: string }[];
}

function controlForQuestion(question: ClarificationQuestion, t: Translate) {
  if (question.type === 'TEXT') {
    return <Input />;
  }
  if (question.type === 'NUMBER') {
    return <InputNumber min={1} className="w-full" />;
  }
  if (question.type === 'DATE_RANGE') {
    return <DatePicker.RangePicker className="w-full" />;
  }
  if (question.type === 'MULTI_CHOICE') {
    return <Select mode="multiple" options={optionsForQuestion(question, t)} />;
  }
  return <Select options={optionsForQuestion(question, t)} />;
}

function optionsForQuestion(question: ClarificationQuestion, t: Translate) {
  return question.options.map((option) => ({ value: option, label: t(labelKeyForQuestion(question, option)) }));
}

function labelKeyForQuestion(question: ClarificationQuestion, option: string): string {
  if (question.id === 'date_flexibility') {
    return enumLabelKey('date_flexibility', option);
  }
  if (question.id === 'interests') {
    return enumLabelKey('travel_interest', option);
  }
  if (question.id === 'pace') {
    return enumLabelKey('travel_pace', option);
  }
  return option;
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
