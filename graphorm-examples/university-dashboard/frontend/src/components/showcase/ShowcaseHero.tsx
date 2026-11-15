import React, { useState } from 'react';
import { Terminal, Copy, Check, ArrowRight, ShieldCheck, Zap, Play, Sparkles } from 'lucide-react';

interface ShowcaseHeroProps {
  onOpenPortal: () => void;
}

export const ShowcaseHero: React.FC<ShowcaseHeroProps> = ({ onOpenPortal }) => {
  const [copied, setCopied] = useState(false);
  const installCmd = 'curl -fsSL https://get.graphorm.dev | bash';

  const handleCopy = () => {
    navigator.clipboard.writeText(installCmd);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section className="relative pt-12 pb-20 md:pt-20 md:pb-28 bg-canvas">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative">
        <div className="text-center max-w-4xl mx-auto space-y-6">
          {/* Release Badge */}
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-surface border border-border">
            <span className="flex h-2 w-2 rounded-full bg-emerald-400" />
            <span className="text-xs font-mono font-medium text-text-muted">
              GraphORM v1.0.0 — Production Ready for Java 21 & Spring Boot 3
            </span>
          </div>

          {/* Primary Statement */}
          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-text-primary leading-[1.12]">
            The Schema-Driven ORM for <br className="hidden sm:inline" />
            <span className="text-accent">
              Java 21 & Spring Boot
            </span>
          </h1>

          {/* Subtitle */}
          <p className="text-base sm:text-lg text-text-muted max-w-2xl mx-auto leading-relaxed">
            Eliminate repetitive DTOs, custom RowMappers, manual DAOs, and hand-wired GraphQL DataFetchers. 
            Define your schema once. GraphORM generates type-safe records, automated GraphQL resolvers, and optimized SQL.
          </p>

          {/* Call to action buttons */}
          <div className="pt-2 flex flex-wrap items-center justify-center gap-3">
            <a
              href="#pipeline"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-text-on-accent bg-accent hover:bg-accent-hover transition-colors cursor-pointer"
            >
              <Zap className="w-4 h-4 text-text-on-accent" />
              <span>Explore Query Pipeline</span>
              <ArrowRight className="w-4 h-4" />
            </a>

            <button
              onClick={() => {
                window.location.hash = 'features-showcase';
              }}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-accent bg-accent/10 hover:bg-accent/20 border border-accent/40 transition-colors cursor-pointer"
            >
              <Sparkles className="w-4 h-4 text-accent" />
              <span>Interactive Feature Lab</span>
            </button>

            <button
              onClick={onOpenPortal}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-text-primary bg-elevated hover:bg-surface border border-border transition-colors cursor-pointer"
            >
              <Play className="w-4 h-4 text-accent" />
              <span>Launch Live Portal</span>
            </button>
          </div>

          {/* CLI Fast Install snippet */}
          <div className="pt-2 flex items-center justify-center">
            <div className="inline-flex items-center gap-3 bg-surface border border-border px-3.5 py-1.5 rounded-md font-mono text-xs text-text-muted">
              <Terminal className="w-3.5 h-3.5 text-text-muted" />
              <span className="text-text-muted">$</span>
              <span className="text-text-primary">{installCmd}</span>
              <button
                onClick={handleCopy}
                aria-label="Copy installation command"
                className="ml-2 text-text-muted hover:text-text-primary transition-colors cursor-pointer"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>
        </div>

        {/* Hero Interactive Terminal & Compilation Box */}
        <div className="mt-12 max-w-3xl mx-auto rounded-lg bg-surface border border-border overflow-hidden font-mono text-xs shadow-md">
          {/* Terminal Window Chrome */}
          <div className="bg-surface px-4 py-2.5 border-b border-border flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full bg-border inline-block" />
              <span className="w-3 h-3 rounded-full bg-border inline-block" />
              <span className="w-3 h-3 rounded-full bg-border inline-block" />
              <span className="text-text-muted text-[11px] ml-2">bash — graphorm-cli v1.0.0</span>
            </div>
            <span className="text-[10px] text-text-muted bg-elevated px-2 py-0.5 rounded border border-border">
              PostgreSQL 15+ Target
            </span>
          </div>

          {/* Terminal Content */}
          <div className="p-4 sm:p-5 space-y-2 text-text-primary leading-relaxed overflow-x-auto bg-canvas">
            <div className="flex items-center gap-2 text-accent font-semibold">
              <span className="text-emerald-400">➜</span>
              <span>graphorm generate --schema src/main/resources/schema.graphorm</span>
            </div>
            <div className="text-text-muted pl-4">
              [1/4] <span className="text-text-primary">ANTLR4 Parser:</span> Validating schema grammar & invariants...
            </div>
            <div className="text-emerald-400 pl-4 flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5" />
              <span>Parsed 5 models (Department, Student, Course, Instructor, Enrollment)</span>
            </div>
            <div className="text-emerald-400 pl-4 flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5" />
              <span>Parsed 2 view models (DepartmentStats [Physical], StudentDashboard [Virtual])</span>
            </div>
            <div className="text-text-muted pl-4">
              [2/4] <span className="text-text-primary">JavaPoet Codegen:</span> Emitting Java 21 immutable records...
            </div>
            <div className="text-text-primary pl-4 font-semibold text-accent">
              Emitted: Student.java, Department.java, StudentDashboard.java, GraphOrmClient.java
            </div>
            <div className="text-text-muted pl-4">
              [3/4] <span className="text-text-primary">Compiler Verification:</span> Invoking JSR-199 in-memory JavaCompiler...
            </div>
            <div className="bg-surface border border-emerald-500/30 rounded p-2.5 mt-2 flex items-center justify-between text-emerald-300">
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Compilation Succeeded: 0 errors, 0 warnings (142ms)</span>
              </div>
              <span className="text-[10px] font-mono text-emerald-400/80">Zero-Leakage Invariant</span>
            </div>
          </div>
        </div>

        {/* Feature Badges Grid */}
        <div className="mt-10 grid grid-cols-2 md:grid-cols-4 gap-4 max-w-4xl mx-auto text-center font-mono text-xs">
          <div className="p-3 rounded-lg bg-surface border border-border">
            <p className="text-text-muted text-[10px] uppercase tracking-wider font-semibold">Language</p>
            <p className="text-text-primary font-bold mt-1 text-sm">Java 21 Records</p>
          </div>
          <div className="p-3 rounded-lg bg-surface border border-border">
            <p className="text-text-muted text-[10px] uppercase tracking-wider font-semibold">Framework</p>
            <p className="text-text-primary font-bold mt-1 text-sm">Spring Boot 3.3.x</p>
          </div>
          <div className="p-3 rounded-lg bg-surface border border-border">
            <p className="text-text-muted text-[10px] uppercase tracking-wider font-semibold">Database Engine</p>
            <p className="text-text-primary font-bold mt-1 text-sm">PostgreSQL 15+</p>
          </div>
          <div className="p-3 rounded-lg bg-surface border border-border">
            <p className="text-text-muted text-[10px] uppercase tracking-wider font-semibold">License</p>
            <p className="text-text-primary font-bold mt-1 text-sm">Apache 2.0</p>
          </div>
        </div>
      </div>
    </section>
  );
};
