import type { AuthSessionResponse, IdentityProvider } from '@/lib/api/auth-api';

/**
 * View-model shapes for `features/auth`.
 *
 * These are **form** types, not API types (PLAN §4.2.6-A): `SignUpFormValues` has a
 * `confirm_password` the contract has never heard of, and `SignInFormValues` names the identifier
 * `login` because that is the contract's field. Anything that mirrors an OpenAPI schema
 * field-for-field belongs in `@/generated/api` instead.
 */

export interface SignInFormValues {
  login: string;
  password: string;
}

export interface SignUpFormValues {
  email: string;
  username: string;
  password: string;
}

export interface EmailFormValues {
  email: string;
}

export interface ResetPasswordFormValues {
  new_password: string;
  confirm_password: string;
}

export interface ChangePasswordFormValues {
  current_password: string;
  new_password: string;
  confirm_password: string;
}

export type AuthMode = 'sign_in' | 'sign_up';

export interface AuthProviderButtonsProps {
  mode: AuthMode;
}

export interface AuthCardProps {
  title: string;
  description?: string;
  children: React.ReactNode;
  /** Rendered under the card — the "already have an account?" style switch. */
  footer?: React.ReactNode;
}

/** What a completed sign-in hands back to the UI, so it can pick the right confirmation copy. */
export type AuthOutcome = Pick<AuthSessionResponse, 'is_new_user' | 'provider_linked'>;

export interface ProviderRowProps {
  provider: IdentityProvider;
  isConnected: boolean;
  /** False when disconnecting would leave the account with no way in (ADR 009 §4). */
  canDisconnect: boolean;
}
