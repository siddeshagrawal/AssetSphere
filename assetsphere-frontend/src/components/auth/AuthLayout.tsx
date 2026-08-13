import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Layers, Search, ShieldCheck, Sparkles } from "lucide-react";

const capabilities = [
  [Sparkles, "Grounded AI over your workspace"],
  [Search, "Searchable organizational knowledge"],
  [ShieldCheck, "Understand and govern every version"],
] as const;

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="grid min-h-screen bg-background lg:grid-cols-[1.05fr_0.95fr]">
      <section className="hidden flex-col justify-between overflow-hidden border-r border-border bg-slate-950 p-12 text-white lg:flex">
        <Link to="/" className="flex w-fit items-center gap-3 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white text-slate-950"><Layers className="h-5 w-5" /></span>
          <span className="text-lg font-semibold">AssetSphere</span>
        </Link>
        <div className="max-w-xl">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">Enterprise knowledge workspace</p>
          <h1 className="mt-5 text-4xl font-semibold leading-tight">Turn trusted assets into knowledge your team can act on.</h1>
          <div className="mt-10 space-y-5">
            {capabilities.map(([Icon, label]) => <div key={label} className="flex items-center gap-3 text-sm text-slate-300"><span className="rounded-lg border border-white/10 bg-white/5 p-2"><Icon className="h-4 w-4" /></span>{label}</div>)}
          </div>
        </div>
        <p className="text-xs text-slate-500">Secure, version-aware, source-grounded.</p>
      </section>
      <section className="flex items-center justify-center px-5 py-10 sm:px-10">
        <div className="w-full max-w-md">
          <Link to="/" className="mb-8 flex items-center gap-2 rounded-md font-semibold lg:hidden"><Layers className="h-5 w-5 text-primary" />AssetSphere</Link>
          {children}
        </div>
      </section>
    </main>
  );
}
