import React from 'react';
import { Cpu, Zap, ShieldCheck, Database, Terminal, Activity, Layers, Lock } from 'lucide-react';

export const FeaturesGrid: React.FC = () => {
  const features = [
    {
      icon: Cpu,
      title: 'Java 21 Immutable Records',
      description: 'JavaPoet emits clean Java 21 records with type-safe fluent criteria builders, .with*() mutators, and zero runtime reflection overhead.',
      tag: 'Zero Reflection',
      color: 'text-sky-400',
    },
    {
      icon: Zap,
      title: 'Automatic Spring for GraphQL',
      description: 'Zero-code GraphQL DataFetchers for queries and mutations. SDL schemas are derived directly from schema.graphorm at startup.',
      tag: 'Zero-Code SDL',
      color: 'text-sky-400',
    },
    {
      icon: Database,
      title: 'Native N+1 Elimination',
      description: 'Automated relationship batching via GraphQL Java DataLoaders turns nested graph traversals into high-performance SQL IN (?, ?, ...) queries.',
      tag: 'DataLoader Batching',
      color: 'text-emerald-400',
    },
    {
      icon: Layers,
      title: 'Join & Projection Pruning',
      description: 'Unselected table joins in virtual views and unrequested columns are automatically omitted from generated SQL ASTs at query execution time.',
      tag: 'AST Optimizer',
      color: 'text-amber-400',
    },
    {
      icon: Lock,
      title: 'Field-Level Security (@auth)',
      description: 'Declarative authorization on sensitive attributes (e.g. salary, gpa) enforced via Spring Security roles, query depth limits, and complexity caps.',
      tag: 'DoS Defense',
      color: 'text-amber-400',
    },
    {
      icon: Terminal,
      title: 'Picocli Developer CLI',
      description: 'Standalone developer tool for project scaffolding (graphorm init), schema validation with caret errors, code generation, and live DB introspection.',
      tag: 'Standalone CLI',
      color: 'text-sky-400',
    },
    {
      icon: Activity,
      title: 'Micrometer Observability',
      description: 'Built-in telemetry for query execution duration, DataLoader batch sizes, cache hit ratios, and configurable SQL statement logging.',
      tag: 'Metrics Ready',
      color: 'text-emerald-400',
    },
    {
      icon: ShieldCheck,
      title: 'Strict Read-Only Views',
      description: 'View models strictly reject write mutations at both compile-time and runtime (ReadOnlyViewException), preserving clean architectural boundaries.',
      tag: 'Type Invariant',
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

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
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
