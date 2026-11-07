import React from 'react';
import { Database, ShieldCheck } from 'lucide-react';
import { GithubIcon } from './GithubIcon';

export const ShowcaseFooter: React.FC = () => {
  return (
    <footer className="py-12 bg-[#0D1117] border-t border-[#30363D] font-mono text-xs text-[#8B949E]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 pb-10 border-b border-[#30363D]">
          {/* Col 1: Brand */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-md bg-[#161B22] border border-[#30363D] flex items-center justify-center text-sky-400">
                <Database className="w-4 h-4 text-sky-400" />
              </div>
              <span className="font-bold text-[#F0F6FC] text-sm">GraphORM</span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                v1.0.0
              </span>
            </div>
            <p className="text-[#8B949E] font-sans text-xs leading-relaxed">
              The schema-driven ORM for Java 21 & Spring Boot. High-performance SQL generation, automated GraphQL, and zero boilerplate.
            </p>
          </div>

          {/* Col 2: Documentation */}
          <div className="space-y-2">
            <p className="font-bold text-[#F0F6FC] uppercase text-[10px] tracking-wider">Documentation</p>
            <ul className="space-y-1.5 text-[#8B949E]">
              <li>
                <a href="#pipeline" className="hover:text-sky-400 transition-colors">Query Pipeline</a>
              </li>
              <li>
                <a href="#view-models" className="hover:text-sky-400 transition-colors">View Model Invariants</a>
              </li>
              <li>
                <a href="#architecture" className="hover:text-sky-400 transition-colors">System Architecture</a>
              </li>
              <li>
                <a href="#quickstart" className="hover:text-sky-400 transition-colors">Quickstart Guide</a>
              </li>
            </ul>
          </div>

          {/* Col 3: Engine Guarantees */}
          <div className="space-y-2">
            <p className="font-bold text-[#F0F6FC] uppercase text-[10px] tracking-wider">Verified Guarantees</p>
            <ul className="space-y-1.5 text-[#8B949E] text-[11px]">
              <li className="flex items-center gap-1.5 text-emerald-400">
                <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
                <span>Zero N+1 via DataLoaders</span>
              </li>
              <li className="flex items-center gap-1.5 text-emerald-400">
                <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
                <span>Join & Projection Pruning</span>
              </li>
              <li className="flex items-center gap-1.5 text-emerald-400">
                <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
                <span>100% Parameterized SQL</span>
              </li>
              <li className="flex items-center gap-1.5 text-emerald-400">
                <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
                <span>JSR-199 In-Memory Tested</span>
              </li>
            </ul>
          </div>

          {/* Col 4: Community & License */}
          <div className="space-y-2">
            <p className="font-bold text-[#F0F6FC] uppercase text-[10px] tracking-wider">Open Source</p>
            <p className="font-sans text-xs text-[#8B949E] leading-relaxed">
              Licensed under the Apache License, Version 2.0. Free for commercial and non-commercial development.
            </p>
            <div className="pt-2">
              <a
                href="https://github.com/jithendra/graphorm"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded bg-[#161B22] border border-[#30363D] hover:border-slate-500 text-[#C9D1D9] hover:text-white transition-colors"
              >
                <GithubIcon className="w-3.5 h-3.5" />
                <span>GitHub Repository</span>
              </a>
            </div>
          </div>
        </div>

        {/* Bottom Credits */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-[#8B949E]">
          <div>
            <span>© 2026 GraphORM Project. Built for Java 21, Spring Boot 3 & PostgreSQL.</span>
          </div>
          <div className="flex items-center gap-4">
            <span>Apache License 2.0</span>
            <span>•</span>
            <a href="https://github.com/jithendra/graphorm/blob/main/LICENSE" className="hover:text-slate-200 transition-colors">
              Terms & License
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};
