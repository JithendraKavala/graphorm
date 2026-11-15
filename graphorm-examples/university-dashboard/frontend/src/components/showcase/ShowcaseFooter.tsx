import React from 'react';
import { Database, ShieldCheck } from 'lucide-react';
import { GithubIcon } from './GithubIcon';

export const ShowcaseFooter: React.FC = () => {
  return (
    <footer className="py-12 bg-canvas border-t border-border font-mono text-xs text-text-muted">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 pb-10 border-b border-border">
          {/* Col 1: Brand */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-md bg-surface border border-border flex items-center justify-center text-accent">
                <Database className="w-4 h-4 text-accent" />
              </div>
              <span className="font-bold text-text-primary text-sm">GraphORM</span>
              <span className="text-[10px] px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                v1.0.0
              </span>
            </div>
            <p className="text-text-muted font-sans text-xs leading-relaxed">
              The schema-driven ORM for Java 21 & Spring Boot. High-performance SQL generation, automated GraphQL, and zero boilerplate.
            </p>
          </div>

          {/* Col 2: Documentation */}
          <div className="space-y-2">
            <p className="font-bold text-text-primary uppercase text-[10px] tracking-wider">Documentation</p>
            <ul className="space-y-1.5 text-text-muted">
              <li>
                <a href="#pipeline" className="hover:text-accent transition-colors">Query Pipeline</a>
              </li>
              <li>
                <a href="#view-models" className="hover:text-accent transition-colors">View Model Invariants</a>
              </li>
              <li>
                <a href="#architecture" className="hover:text-accent transition-colors">System Architecture</a>
              </li>
              <li>
                <a href="#quickstart" className="hover:text-accent transition-colors">Quickstart Guide</a>
              </li>
            </ul>
          </div>

          {/* Col 3: Engine Guarantees */}
          <div className="space-y-2">
            <p className="font-bold text-text-primary uppercase text-[10px] tracking-wider">Verified Guarantees</p>
            <ul className="space-y-1.5 text-text-muted text-[11px]">
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
            <p className="font-bold text-text-primary uppercase text-[10px] tracking-wider">Open Source</p>
            <p className="font-sans text-xs text-text-muted leading-relaxed">
              Licensed under the Apache License, Version 2.0. Free for commercial and non-commercial development.
            </p>
            <div className="pt-2">
              <a
                href="https://github.com/graphorm/graphorm"
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded bg-surface border border-border hover:border-accent text-text-primary hover:text-white transition-colors"
              >
                <GithubIcon className="w-3.5 h-3.5" />
                <span>GitHub Repository</span>
              </a>
            </div>
          </div>
        </div>

        {/* Bottom Credits */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-text-muted">
          <div>
            <span>© 2026 GraphORM Project. Built for Java 21, Spring Boot 3 & PostgreSQL.</span>
          </div>
          <div className="flex items-center gap-4">
            <span>Apache License 2.0</span>
            <span>•</span>
            <a href="https://github.com/graphorm/graphorm/blob/main/LICENSE" className="hover:text-slate-200 transition-colors">
              Terms & License
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};
