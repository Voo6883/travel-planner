/**
 * Firebase **client** SDK only (ADR 004, PLAN §4.0.5).
 *
 * The Admin SDK and the service-account JSON stay in `infrastructure/auth/firebase/` on the
 * backend. What lives here is public by design: `NEXT_PUBLIC_FIREBASE_*` values are identifiers,
 * not secrets, and the ID token this file obtains is worthless without the backend's verification
 * — which asserts `aud` and `firebase.sign_in_provider` before trusting a single claim
 * (ADR 009 §4).
 *
 * **Imported dynamically, on click.** The Firebase auth bundle is large, it is needed only if a
 * user actually chooses Google, and a static import would put it in the first load of every
 * authentication page — including the reset and verification pages, which never touch it.
 *
 * Config is optional. `docker compose up` works with no Firebase project, so the button asks
 * `isFirebaseConfigured()` and renders a disabled control with an explanation rather than
 * throwing on click.
 */

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? '',
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? '',
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? '',
};

export function isFirebaseConfigured(): boolean {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.authDomain && firebaseConfig.projectId);
}

/** Errors Firebase raises for a user action rather than a fault, so the UI can stay calm. */
export const FIREBASE_POPUP_CANCELLED = new Set([
  'auth/popup-closed-by-user',
  'auth/cancelled-popup-request',
  'auth/user-cancelled',
]);

export const FIREBASE_POPUP_BLOCKED = 'auth/popup-blocked';

/**
 * A Firebase ID token for the signed-in Google account.
 *
 * `prompt: select_account` is deliberate: without it a user with several Google accounts is
 * silently signed in as whichever one the browser remembers, which is how people end up with two
 * Travel Planner accounts and no idea why their trips vanished.
 */
export async function requestGoogleIdToken(): Promise<string> {
  const [{ getApp, getApps, initializeApp }, { getAuth, GoogleAuthProvider, signInWithPopup }] =
    await Promise.all([import('firebase/app'), import('firebase/auth')]);

  const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });

  const credential = await signInWithPopup(getAuth(app), provider);
  return credential.user.getIdToken();
}
