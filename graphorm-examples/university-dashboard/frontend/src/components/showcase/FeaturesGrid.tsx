import React from 'react';
import { Cpu, Zap, ShieldCheck, Database, Terminal, Activity, Layers, Lock, GitBranch } from 'lucide-react';

export const FeaturesGrid: React.FC = () => {
  const features = [
    {
      icon: Database,
      title: 'P0 Cryptographic Migrations',
      description: 'Zero-downtime versioned migrations ledger (_graphorm_migrations) with SHA-256 checksums, tamper protection, and database advisory locking.',
      tag: 'P0 Ledger Engine',
      color: 'text-sky-400',
    },
    {
      icon: Zap,
      title: 'P1 Relay Keyset Pagination',
      description: 'Relay-compliant Connection, Edge, and PageInfo specifications using base64 opaque cursors for O(1) performance over arbitrary dataset offsets.',
      tag: 'P1 Keyset Spec',
      color: 'text-sky-400',
    },
    {
      icon: Layers,
      title: 'P1 Upsert & Atomic Batching',
      description: 'Idempotent UpsertQuery targeting unique index constraints (ON CONFLICT DO UPDATE) and multi-row batch inserts within a single atomic transaction.',
      tag: 'P1 High Throughput',
      color: 'text-emerald-400',
    },
    {
      icon: Lock,
      title: 'P2 @version Optimistic Locking',
      description: 'Declarative concurrency control on records. GraphORM checks version columns, throwing OptimisticLockException translated to HTTP 409.',
      tag: 'P2 Concurrency',
      color: 'text-purple-400',
    },
    {
      icon: Cpu,
      title: 'P3 Maven Plugin & Clean Build',
      description: 'graphorm-maven-plugin generates immutable Java 21 model records, views, and type-safe client APIs cleanly during mvn compile.',
      tag: 'P3 Build Tooling',
      color: 'text-amber-400',
    },
    {
      icon: GitBranch,
      title: 'Multi-Dialect SPI (PG / MySQL / SQLite)',
      description: 'QueryExecutionEngine SPI with PostgreSQL 15+, MySQL 8+, and SQLite dialects. Auto-detected via JDBC metadata with Loom virtual-thread safety.',
      tag: 'Phase 21 Dialects',
      color: 'text-sky-400',
    },
    {
      icon: Activity,
      title: 'Real-Time CDC (@@notify & SSE)',
      description: 'PostgreSQL LISTEN/NOTIFY integrated with Spring SSE and EntityEventBroker, streaming live mutations to web clients with zero polling.',
      tag: 'Phase 24 Realtime',
      color: 'text-rose-400',
    },
    {
      icon: ShieldCheck,
      title: 'Multi-Tenant Isolation (@tenantId)',
      description: 'Thread-local TenantContext scopes all queries and mutations with transparent partition filtering and cross-tenant leakage prevention.',
      tag: 'Phase 22 Isolation',
      color: 'text-indigo-400',
    },
    {
      icon: Terminal,
      title: 'L2 Cache & Mutation Eviction',
      description: 'Caffeine L2 caching for @cached view models with automatic tag-based cache eviction whenever underlying source models are mutated.',
      tag: 'Phase 23 Sub-ms L2',
      color: 'text-emerald-400',
    },
  ];


  return (
    <section id="features" className="py-20 bg-[#0D1117] border-t border-[#30363D]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-[#161B22] border border-emerald-500/30 text-emerald-400 font-mono text-xs font-semibold">
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Enterprise Engine</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-[#F0F6FC] tracking-tight">
            Engineered for High-Velocity Backend Teams
          </h2>

          <p className="text-[#8B949E] text-sm sm:text-base leading-relaxed">
            Every capability in GraphORM is backed by rigorous integration tests, JSR-199 in-memory compilation checks, and real PostgreSQL execution.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {features.map((feature) => {
            const Icon = feature.icon;
            return (
              <div
                key={feature.title}
                className="p-5 rounded-lg bg-[#161B22] border border-[#30363D] hover:border-slate-500 transition-colors flex flex-col justify-between group shadow-sm"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <div className="w-9 h-9 rounded-md bg-[#0D1117] border border-[#30363D] flex items-center justify-center">
                      <Icon className={`w-5 h-5 ${feature.color}`} />
                    </div>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#0D1117] text-[#8B949E] border border-[#30363D]">
                      {feature.tag}
                    </span>
                  </div>

                  <h3 className="text-sm font-semibold text-[#F0F6FC] group-hover:text-white transition-colors">
                    {feature.title}
                  </h3>

                  <p className="text-xs text-[#8B949E] leading-relaxed mt-2 font-sans">
                    {feature.description}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};
