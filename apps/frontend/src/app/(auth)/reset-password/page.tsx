import { Suspense } from 'react';
import { ResetPasswordPanel } from '@/features/auth';

/**
 * `/reset-password?token=…` — UC-A07. The landing for the mailed reset link; the token is read
 * from the query string and posted to `POST /auth/password/reset`.
 */
export default function ResetPasswordPage() {
  return (
    <Suspense>
      <ResetPasswordPanel />
    </Suspense>
  );
}
