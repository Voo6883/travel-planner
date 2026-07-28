import { Suspense } from 'react';
import { SignUpPanel } from '@/features/auth';

/** `/sign-up` — UC-A01, plus Google and GitHub sign-up through the shared provider buttons. */
export default function SignUpPage() {
  return (
    <Suspense>
      <SignUpPanel />
    </Suspense>
  );
}
