import { fetchHealthStatus } from "@/lib/api/health-api";

export async function StatusPanel() {
  let status = "UNKNOWN";
  let errorMessage: string | null = null;

  try {
    const health = await fetchHealthStatus();
    status = health.status;
  } catch (error) {
    errorMessage = error instanceof Error ? error.message : "Backend unreachable";
  }

  return (
    <section className="w-full max-w-xl rounded-xl border border-zinc-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-semibold text-zinc-900">Travel Planner</h1>
      <p className="mt-2 text-sm text-zinc-600">
        Dev mode — edit files and save to see hot reload in the browser.
      </p>
      <dl className="mt-6 space-y-3 text-sm">
        <div className="flex justify-between gap-4">
          <dt className="text-zinc-500">Frontend</dt>
          <dd className="font-medium text-emerald-600">HMR active (Next.js dev)</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-zinc-500">Backend /api/v1/health</dt>
          <dd className={`font-medium ${errorMessage ? "text-red-600" : "text-emerald-600"}`}>
            {errorMessage ?? status}
          </dd>
        </div>
      </dl>
    </section>
  );
}
