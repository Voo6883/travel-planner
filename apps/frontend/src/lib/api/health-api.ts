export interface HealthStatus {
  status: string;
}

export async function fetchHealthStatus(): Promise<HealthStatus> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1";
  const response = await fetch(`${baseUrl}/health`, { cache: "no-store" });

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`);
  }

  return response.json() as Promise<HealthStatus>;
}
