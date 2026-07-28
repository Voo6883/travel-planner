import { Suspense } from 'react';
import { VerifyEmailPanel } from '@/features/auth';

/**
 * `/verify-email?token=…` — UC-A08, and `/verify-email` with no token is the resend form
 * (UC-A13), which is where the sign-in page's `email_not_verified` state links to.
 */
export default function VerifyEmailPage() {
  return (
    <Suspense>
      <VerifyEmailPanel />
    </Suspense>
  );
}
