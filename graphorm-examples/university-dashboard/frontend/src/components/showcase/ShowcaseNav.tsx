import React, { useState } from 'react';
import { Database, ArrowUpRight, Menu, X, Sparkles } from 'lucide-react';
import { GithubIcon } from './GithubIcon';

interface ShowcaseNavProps {
  onOpenPortal: () => void;
}

export const ShowcaseNav: React.FC<ShowcaseNavProps> = ({ onOpenPortal }) => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const navLinks = [
    { label: 'Query Pipeline', href: '#pipeline' },
    { label: 'Boilerplate Tax', href: '#comparison' },
    { label: 'View Models', href: '#view-models' },
    { label: 'Architecture', href: '#architecture' },
    { label: 'Features', href: '#features' },
    { label: 'Quickstart', href: '#quickstart' },
  ];

  return (
    <header className="sticky top-0 z-50 bg-[#0D1117]/95 backdrop-blur-md border-b border-[#30363D]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand / Logo */}
        <div className="flex items-center gap-3">
          <a href="#" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-lg bg-[#161B22] border border-[#30363D] flex items-center justify-center text-sky-400 group-hover:border-sky-400 transition-colors">
              <Database className="w-5 h-5 text-sky-400" />
            </div>
            <div className="flex items-baseline gap-2">
              <span className="font-mono font-bold text-lg text-[#F0F6FC] tracking-tight">
                Graph<span className="text-sky-400">ORM</span>
              </span>
              <span className="text-[10px] font-mono font-semibold px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                v1.0.0
              </span>
            </div>
          </a>
        </div>

        {/* Desktop Navigation Links */}
        <nav className="hidden lg:flex items-center gap-7">
          {navLinks.map((link) => (
            <a
              key={link.label}
              href={link.href}
              className="text-sm font-medium text-[#8B949E] hover:text-[#F0F6FC] transition-colors"
            >
              {link.label}
            </a>
          ))}
        </nav>

        {/* Action Controls */}
        <div className="hidden sm:flex items-center gap-3">
          <a
            href="https://github.com/jithendra/graphorm"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-mono font-medium text-[#F0F6FC] bg-[#161B22] hover:bg-[#21262D] border border-[#30363D] hover:border-slate-500 transition-colors cursor-pointer"
          >
            <GithubIcon className="w-4 h-4" />
            <span>GitHub</span>
          </a>

          <button
            onClick={onOpenPortal}
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-md text-xs font-semibold text-slate-950 bg-sky-400 hover:bg-sky-300 transition-colors cursor-pointer"
          >
            <Sparkles className="w-3.5 h-3.5 text-slate-950" />
            <span>Launch Live Portal</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Mobile Hamburger Button */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label="Toggle navigation menu"
          className="lg:hidden p-2 text-[#8B949E] hover:text-white hover:bg-[#21262D] rounded-md transition-colors"
        >
          {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="lg:hidden bg-[#161B22] border-b border-[#30363D] px-4 pt-3 pb-6 space-y-3">
          <nav className="flex flex-col space-y-2">
            {navLinks.map((link) => (
              <a
                key={link.label}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="px-3 py-2 text-sm font-medium text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] rounded-md transition-colors"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <div className="pt-3 border-t border-[#30363D] flex flex-col gap-2">
            <button
              onClick={() => {
                setMobileMenuOpen(false);
                onOpenPortal();
              }}
              className="w-full flex items-center justify-center gap-2 py-2 px-4 rounded-md text-sm font-semibold text-slate-950 bg-sky-400 hover:bg-sky-300 transition-colors cursor-pointer"
            >
              <Sparkles className="w-4 h-4 text-slate-950" />
              <span>Launch Live University Portal</span>
            </button>
            <a
              href="https://github.com/jithendra/graphorm"
              target="_blank"
              rel="noreferrer"
              className="w-full flex items-center justify-center gap-2 py-2 px-4 rounded-md text-sm font-mono text-[#F0F6FC] bg-[#21262D] hover:bg-slate-700 border border-[#30363D] transition-colors"
            >
              <GithubIcon className="w-4 h-4" />
              <span>View GitHub Source</span>
            </a>
          </div>
        </div>
      )}
    </header>
  );
};
