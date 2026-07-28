import { StatusPanel } from "@/features/status";

export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-zinc-50 p-6">
      <StatusPanel />
    </main>
  );
}
