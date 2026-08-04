'use client';

import { Button, Card, Form, Input, List, Tag } from 'antd';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import type { Trip } from '@/lib/api/trip-api';
import { useCreateTrip, useTrips } from '../hooks/use-trips';

interface CreateTripFormValues {
  name: string;
}

/**
 * Manual trip creation is a fallback (UC-T01b), but the list is primary navigation (UC-T02). The
 * panel keeps both backed by server state so later chat-created trips appear through the same cache.
 */
export function TripListPanel() {
  const t = useTranslations('trip_brief');
  const router = useRouter();
  const [form] = Form.useForm<CreateTripFormValues>();
  const trips = useTrips();
  const create = useCreateTrip();

  async function submit(values: CreateTripFormValues) {
    const trip = await create.mutateAsync({ name: values.name.trim() });
    form.resetFields();
    router.push(`/trips/${trip.trip_id}`);
  }

  if (trips.isPending) {
    return <LoadingState label={t('states.loading')} rows={5} />;
  }

  if (trips.isError) {
    return <ErrorAlert error={trips.error} onRetry={() => void trips.refetch()} />;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,360px)_1fr]">
      <Card title={t('create.title')} className="h-fit">
        <Form form={form} layout="vertical" onFinish={submit}>
          <Form.Item
            name="name"
            label={t('create.name_label')}
            rules={[{ required: true, whitespace: true, message: t('create.name_required') }]}
          >
            <Input placeholder={t('create.name_placeholder')} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={create.isPending} className="min-h-control">
            {t('create.submit')}
          </Button>
        </Form>
      </Card>
      <TripList trips={trips.data.trips} />
    </div>
  );
}

function TripList({ trips }: { trips: Trip[] }) {
  const t = useTranslations('trip_brief');

  if (trips.length === 0) {
    return <EmptyState title={t('states.empty_title')} description={t('states.empty_body')} />;
  }

  return (
    <Card title={t('list.title')}>
      <List
        itemLayout="vertical"
        dataSource={trips}
        renderItem={(trip) => (
          <List.Item key={trip.trip_id} actions={[<TripLink key="open" trip={trip} />]}>
            <List.Item.Meta
              title={<span className="text-title text-foreground">{trip.name}</span>}
              description={<TripStatusTag trip={trip} />}
            />
          </List.Item>
        )}
      />
    </Card>
  );
}

function TripLink({ trip }: { trip: Trip }) {
  const t = useTranslations('trip_brief');

  return (
    <Link href={`/trips/${trip.trip_id}`} className="inline-flex min-h-control items-center text-body-sm">
      {t('list.open_action')}
    </Link>
  );
}

function TripStatusTag({ trip }: { trip: Trip }) {
  const t = useTranslations('trip_brief');

  return <Tag color={statusColor(trip.status)}>{t(`status.${trip.status.toLowerCase()}`)}</Tag>;
}

function statusColor(status: Trip['status']): string | undefined {
  if (status === 'CLARIFICATION_NEEDED') {
    return 'warning';
  }
  if (status === 'BRIEF_COMPLETE') {
    return 'processing';
  }
  if (status === 'ARCHIVED') {
    return 'default';
  }
  return undefined;
}
