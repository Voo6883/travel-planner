import { LandingFooter, LandingHero, LandingSteps, LandingTrust, MarketingHeader } from '@/features/marketing';

/**
 * Marketing landing (§8.1) — the one route that needs no session.
 *
 * Composition only: no data fetching, no business logic (PLAN §4.2.2). Hero, three-step
 * explainer, trust section, and composed chat preview follow the design-system blueprint.
 */
export default function HomePage() {
  return (
    <div className="min-h-dvh w-full">
      <MarketingHeader />
      <main id="main-content">
        <LandingHero />
        <LandingSteps />
        <LandingTrust />
        <LandingFooter />
      </main>
    </div>
  );
}
