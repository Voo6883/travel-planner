import type { ClarificationAnswer, ClarificationQuestion, DateRange, Money } from '../types';

export interface MoneyAnswerValue {
  amount?: string;
  currency?: string;
}

export type ClarificationFieldValue = string | number | string[] | DateRange | MoneyAnswerValue | null | undefined;

export type ClarificationFormValues = Record<string, ClarificationFieldValue>;

/**
 * Clarification answers are typed diffs (ADR 008 §3), not a second full brief save. The question's
 * `type` decides which slot is legal; everything else stays absent so the server can reject shape
 * drift instead of guessing.
 */
export function buildClarificationAnswers(
  questions: readonly ClarificationQuestion[],
  values: ClarificationFormValues,
): ClarificationAnswer[] {
  return questions.map((question) => answerForQuestion(question, values[question.id]));
}

export function promptKey(promptKeyValue: string): string {
  return promptKeyValue.replace(/^trip_brief\./, '');
}

export function enumLabelKey(group: string, value: string): string {
  return `enum.${group}.${value.toLowerCase()}`;
}

function answerForQuestion(question: ClarificationQuestion, value: ClarificationFieldValue): ClarificationAnswer {
  const base = { question_id: question.id };

  switch (question.type) {
    case 'TEXT':
      return { ...base, text: stringValue(value) };
    case 'NUMBER':
      return { ...base, number: numberValue(value) };
    case 'MONEY':
      return { ...base, money: moneyValue(value) };
    case 'DATE_RANGE':
      return { ...base, date_range: dateRangeValue(value) };
    case 'CHOICE':
      return { ...base, choice: stringValue(value) };
    case 'MULTI_CHOICE':
      return { ...base, choices: stringArrayValue(value) };
  }
}

function stringValue(value: ClarificationFieldValue): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function numberValue(value: ClarificationFieldValue): number | null {
  return typeof value === 'number' ? value : null;
}

function stringArrayValue(value: ClarificationFieldValue): string[] | null {
  return Array.isArray(value) ? value.filter((entry) => entry.trim()) : null;
}

function moneyValue(value: ClarificationFieldValue): Money | null {
  if (!isMoneyAnswerValue(value)) {
    return null;
  }
  const amount = value.amount?.trim() ?? '';
  return amount ? { amount, currency: value.currency ?? 'MYR' } : null;
}

function dateRangeValue(value: ClarificationFieldValue): DateRange | null {
  return isDateRange(value) ? value : null;
}

function isMoneyAnswerValue(value: ClarificationFieldValue): value is MoneyAnswerValue {
  return typeof value === 'object' && value !== null && !Array.isArray(value) && !isDateRange(value);
}

function isDateRange(value: ClarificationFieldValue): value is DateRange {
  return (
    typeof value === 'object' && value !== null && !Array.isArray(value) && 'start_date' in value && 'end_date' in value
  );
}
