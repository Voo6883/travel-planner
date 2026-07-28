/**
 * Provider enum → `auth` translation key.
 *
 * §6.4: "use title case for named statuses shown to users, not raw enum values". `FIREBASE_GOOGLE`
 * is an implementation detail of how the token is verified; the user connected *Google*.
 *
 * Falls back to the generic local label rather than rendering the raw enum, so a provider added
 * to the contract before its translation lands shows something readable instead of shouting
 * `SNAKE_CASE` at the user.
 */
export function providerLabelKey(provider: string): string {
  switch (provider) {
    case 'FIREBASE_GOOGLE':
      return 'provider_firebase_google';
    case 'GITHUB':
      return 'provider_github';
    default:
      return 'provider_local';
  }
}
