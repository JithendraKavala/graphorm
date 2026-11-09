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
    <section className="relative pt-12 pb-20 md:pt-20 md:pb-28 bg-[#0D1117]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative">
        <div className="text-center max-w-4xl mx-auto space-y-6">
          {/* Release Badge */}
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-[#161B22] border border-[#30363D]">
            <span className="flex h-2 w-2 rounded-full bg-emerald-400" />
            <span className="text-xs font-mono font-medium text-[#8B949E]">
              GraphORM v1.0.0 — Production Ready for Java 21 & Spring Boot 3
            </span>
          </div>

          {/* Primary Statement */}
          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-[#F0F6FC] leading-[1.12]">
            The Schema-Driven ORM for <br className="hidden sm:inline" />
            <span className="text-sky-400">
              Java 21 & Spring Boot
            </span>
          </h1>

          {/* Subtitle */}
          <p className="text-base sm:text-lg text-[#8B949E] max-w-2xl mx-auto leading-relaxed">
            Eliminate repetitive DTOs, custom RowMappers, manual DAOs, and hand-wired GraphQL DataFetchers. 
            Define your schema once. GraphORM generates type-safe records, automated GraphQL resolvers, and optimized SQL.
          </p>

          {/* Call to action buttons */}
          <div className="pt-2 flex flex-wrap items-center justify-center gap-3">
            <a
              href="#pipeline"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-slate-950 bg-emerald-400 hover:bg-emerald-300 transition-colors cursor-pointer"
            >
              <Zap className="w-4 h-4 text-slate-950" />
              <span>Explore Query Pipeline</span>
              <ArrowRight className="w-4 h-4" />
            </a>

            <button
              onClick={() => {
                window.location.hash = 'features-showcase';
              }}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-sky-300 bg-sky-950/40 hover:bg-sky-900/50 border border-sky-500/40 transition-colors cursor-pointer"
            >
              <Sparkles className="w-4 h-4 text-sky-400" />
              <span>Interactive Feature Lab</span>
            </button>

            <button
              onClick={onOpenPortal}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-md text-sm font-semibold text-[#F0F6FC] bg-[#21262D] hover:bg-[#30363D] border border-[#30363D] transition-colors cursor-pointer"
            >
              <Play className="w-4 h-4 text-sky-400" />
              <span>Launch Live Portal</span>
            </button>
          </div>

          {/* CLI Fast Install snippet */}
          <div className="pt-2 flex items-center justify-center">
            <div className="inline-flex items-center gap-3 bg-[#161B22] border border-[#30363D] px-3.5 py-1.5 rounded-md font-mono text-xs text-[#8B949E]">
              <Terminal className="w-3.5 h-3.5 text-[#8B949E]" />
              <span className="text-[#6E7681]">$</span>
              <span className="text-[#F0F6FC]">{installCmd}</span>
              <button
                onClick={handleCopy}
                aria-label="Copy installation command"
                className="ml-2 text-[#8B949E] hover:text-[#F0F6FC] transition-colors cursor-pointer"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>
        </div>

        {/* Hero Interactive Terminal & Compilation Box */}
        <div className="mt-12 max-w-3xl mx-auto rounded-lg bg-[#161B22] border border-[#30363D] overflow-hidden font-mono text-xs shadow-md">
          {/* Terminal Window Chrome */}
          <div className="bg-[#161B22] px-4 py-2.5 border-b border-[#30363D] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full bg-[#30363D] inline-block" />
              <span className="w-3 h-3 rounded-full bg-[#30363D] inline-block" />
              <span className="w-3 h-3 rounded-full bg-[#30363D] inline-block" />
              <span className="text-[#8B949E] text-[11px] ml-2">bash — graphorm-cli v1.0.0</span>
            </div>
            <span className="text-[10px] text-[#8B949E] bg-[#21262D] px-2 py-0.5 rounded border border-[#30363D]">
              PostgreSQL 15+ Target
            </span>
          </div>

          {/* Terminal Content */}
          <div className="p-4 sm:p-5 space-y-2 text-[#F0F6FC] leading-relaxed overflow-x-auto bg-[#0A0D12]">
            <div className="flex items-center gap-2 text-sky-400 font-semibold">
              <span className="text-emerald-400">➜</span>
              <span>graphorm generate --schema src/main/resources/schema.graphorm</span>
            </div>
            <div className="text-[#8B949E] pl-4">
              [1/4] <span className="text-slate-300">ANTLR4 Parser:</span> Validating schema grammar & invariants...
            </div>
            <div className="text-emerald-400 pl-4 flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5" />
              <span>Parsed 5 models (Department, Student, Course, Instructor, Enrollment)</span>
            </div>
            <div className="text-emerald-400 pl-4 flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5" />
              <span>Parsed 2 view models (DepartmentStats [Physical], StudentDashboard [Virtual])</span>
            </div>
            <div className="text-[#8B949E] pl-4">
              [2/4] <span className="text-slate-300">JavaPoet Codegen:</span> Emitting Java 21 immutable records...
            </div>
            <div className="text-slate-200 pl-4 font-semibold text-sky-300">
              Emitted: Student.java, Department.java, StudentDashboard.java, GraphOrmClient.java
            </div>
            <div className="text-[#8B949E] pl-4">
              [3/4] <span className="text-slate-300">Compiler Verification:</span> Invoking JSR-199 in-memory JavaCompiler...
            </div>
            <div className="bg-[#161B22] border border-emerald-500/30 rounded p-2.5 mt-2 flex items-center justify-between text-emerald-300">
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
          <div className="p-3 rounded-lg bg-[#161B22] border border-[#30363D]">
            <p className="text-[#8B949E] text-[10px] uppercase tracking-wider font-semibold">Language</p>
            <p className="text-[#F0F6FC] font-bold mt-1 text-sm">Java 21 Records</p>
          </div>
          <div className="p-3 rounded-lg bg-[#161B22] border border-[#30363D]">
            <p className="text-[#8B949E] text-[10px] uppercase tracking-wider font-semibold">Framework</p>
            <p className="text-[#F0F6FC] font-bold mt-1 text-sm">Spring Boot 3.3.x</p>
          </div>
          <div className="p-3 rounded-lg bg-[#161B22] border border-[#30363D]">
            <p className="text-[#8B949E] text-[10px] uppercase tracking-wider font-semibold">Database Engine</p>
            <p className="text-[#F0F6FC] font-bold mt-1 text-sm">PostgreSQL 15+</p>
          </div>
          <div className="p-3 rounded-lg bg-[#161B22] border border-[#30363D]">
            <p className="text-[#8B949E] text-[10px] uppercase tracking-wider font-semibold">License</p>
            <p className="text-[#F0F6FC] font-bold mt-1 text-sm">Apache 2.0</p>
          </div>
        </div>
      </div>
    </section>
  );
};
