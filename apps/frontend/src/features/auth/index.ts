/**
 * Public surface of `features/auth` (PLAN §4.2.6-B4).
 *
 * Only what a route or another layer legitimately composes is listed. Form components, provider
 * buttons, and the firebase client stay internal — importing them from outside would let a page
 * assemble half an auth flow without the hook that owns its rules.
 */
export { AccountSettingsPanel } from './components/account-settings-panel';
export { AuthGuard } from './components/auth-guard';
export { ForgotPasswordPanel } from './components/forgot-password-panel';
export { ProviderLinkedNotice } from './components/provider-linked-notice';
export { ResetPasswordPanel } from './components/reset-password-panel';
export { SignInPanel } from './components/sign-in-panel';
export { SignUpPanel } from './components/sign-up-panel';
export { VerifyEmailPanel } from './components/verify-email-panel';

export { useCurrentUser } from './hooks/use-current-user';
export { useSignOut } from './hooks/use-sign-out';
