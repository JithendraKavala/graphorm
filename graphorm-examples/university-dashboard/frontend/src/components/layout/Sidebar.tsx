import React from 'react';
import {
  LayoutDashboard,
  Users,
  Building2,
  BookOpen,
  GraduationCap,
  BarChart3,
  Code2,
  Terminal,
  Database,
  Sliders,
  ShieldCheck,
  X,
  Sparkles,
  Zap,
  Sun,
  Moon,
} from 'lucide-react';
import { ActiveTab, DataSourceMode } from '../../types/graphorm';
import { useTheme } from '../../contexts/ThemeContext';

interface SidebarProps {
  activeTab: ActiveTab;
  setActiveTab: (tab: ActiveTab) => void;
  dataSourceMode: DataSourceMode;
  setDataSourceMode: (mode: DataSourceMode) => void;
  isConnected: boolean;
  onOpenCustomizer: () => void;
  isMobileOpen?: boolean;
  onCloseMobile?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeTab,
  setActiveTab,
  dataSourceMode,
  setDataSourceMode,
  isConnected,
  onOpenCustomizer,
  isMobileOpen = false,
  onCloseMobile,
}) => {
  const { theme, toggleTheme } = useTheme();

  const registryItems = [
    { id: 'dashboard' as ActiveTab, label: 'Executive Overview', icon: LayoutDashboard, badge: 'KPI' },
    { id: 'students' as ActiveTab, label: 'Student Directory', icon: Users, badge: 'Scholars' },
    { id: 'departments' as ActiveTab, label: 'Academic Faculties', icon: Building2, badge: 'Schools' },
    { id: 'courses' as ActiveTab, label: 'Curriculum Catalog', icon: BookOpen, badge: 'Units' },
    { id: 'enrollments' as ActiveTab, label: 'Registrar Records', icon: GraduationCap, badge: 'Grades' },
  ];

  const engineItems = [
    { id: 'features-showcase' as ActiveTab, label: 'Live Feature Lab', icon: Zap, badge: 'P0 - P3' },
    { id: 'landing' as ActiveTab, label: 'Product Showcase', icon: Sparkles, badge: 'Overview' },
    { id: 'analytics' as ActiveTab, label: 'View Models & Analytics', icon: BarChart3, badge: 'Stage 1 & 2' },
    { id: 'schema-explorer' as ActiveTab, label: 'Schema Engine & AST', icon: Code2, badge: 'DSL' },
    { id: 'graphql-playground' as ActiveTab, label: 'GraphQL Studio', icon: Terminal, badge: 'Execute' },
  ];

  React.useEffect(() => {
    if (!isMobileOpen || !onCloseMobile) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCloseMobile();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isMobileOpen, onCloseMobile]);

  const handleNavClick = (tab: ActiveTab) => {
    setActiveTab(tab);
    if (onCloseMobile) onCloseMobile();
  };

  return (
    <>
      {/* Mobile Backdrop */}
      {isMobileOpen && (
        <button
          type="button"
          aria-label="Close sidebar"
          onClick={onCloseMobile}
          className="fixed inset-0 bg-canvas/80 backdrop-blur-sm z-40 lg:hidden cursor-default w-full h-full border-none p-0 focus:outline-none"
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed lg:sticky top-0 left-0 z-50 h-screen w-64 bg-sidebar border-r border-border flex flex-col transition-transform duration-300 ease-in-out ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
        }`}
      >
        {/* Brand Header */}
        <div className="h-16 px-4 flex items-center justify-between border-b border-border bg-sidebar">
          <button
            onClick={() => handleNavClick('landing')}
            className="flex items-center gap-3 text-left group cursor-pointer"
          >
            <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-elevated border border-border text-accent group-hover:border-accent transition-colors">
              <GraduationCap className="w-4 h-4 text-accent" />
            </div>

            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-mono font-bold text-sm tracking-tight text-text-primary uppercase group-hover:text-accent-secondary transition-colors">
                  GraphORM
                </span>
                <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-accent/10 text-accent border border-accent/30">
                  v1.0.0
                </span>
              </div>
              <p className="text-[10px] text-text-muted tracking-wider uppercase font-semibold">
                University Portal
              </p>
            </div>
          </button>

          {/* Close button on mobile */}
          <button
            onClick={onCloseMobile}
            className="lg:hidden p-1.5 text-text-muted hover:text-text-primary rounded-lg hover:bg-elevated"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Engine Connectivity Status Card */}
        <div className="p-3 mx-3 mt-3 rounded-lg bg-canvas border border-border shadow-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-accent shadow-[0_0_8px_rgba(45,212,160,0.5)]' : 'bg-amber-400'}`} />
              <div>
                <p className="text-[11px] font-semibold text-text-primary">
                  {isConnected ? 'PostgreSQL 16 Live' : 'GraphORM Client Invariant'}
                </p>
                <p className="text-[10px] font-mono text-text-muted">
                  {isConnected ? '127.0.0.1:8080' : 'In-Memory Type AST'}
                </p>
              </div>
            </div>
            <Database className="w-3.5 h-3.5 text-text-muted" />
          </div>

          {/* Protocol Toggle (REST vs GraphQL) */}
          <div className="mt-2.5 pt-2 border-t border-border flex items-center gap-1 bg-surface p-1 rounded-md border border-border">
            <button
              onClick={() => setDataSourceMode('graphql')}
              className={`flex-1 py-1 rounded text-[10px] font-mono font-semibold tracking-wide transition-all ${
                dataSourceMode === 'graphql'
                  ? 'bg-elevated text-accent border border-accent/30 shadow-sm'
                  : 'text-text-muted hover:text-text-primary'
              }`}
            >
              GraphQL
            </button>
            <button
              onClick={() => setDataSourceMode('rest')}
              className={`flex-1 py-1 rounded text-[10px] font-mono font-semibold tracking-wide transition-all ${
                dataSourceMode === 'rest'
                  ? 'bg-elevated text-accent border border-accent/30 shadow-sm'
                  : 'text-text-muted hover:text-text-primary'
              }`}
            >
              REST API
            </button>
          </div>

          {/* Theme Toggle Button */}
          <div className="mt-2 pt-2 border-t border-border">
            <button
              onClick={toggleTheme}
              className="w-full flex items-center justify-center gap-2 py-1.5 px-3 rounded text-[11px] font-mono font-semibold tracking-wide transition-all bg-surface hover:bg-elevated border border-border text-text-primary shadow-sm cursor-pointer"
              title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`}
            >
              {theme === 'dark' ? (
                <>
                  <Sun className="w-3.5 h-3.5 text-accent" />
                  <span>Light mode</span>
                </>
              ) : (
                <>
                  <Moon className="w-3.5 h-3.5 text-accent" />
                  <span>Dark mode</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Navigation Sections */}
        <nav className="flex-1 overflow-y-auto px-3 py-3 space-y-4">
          {/* Section 1: Academic Registry */}
          <div>
            <div className="px-2.5 pb-1.5 text-[9px] font-bold text-text-muted uppercase tracking-widest font-mono">
              Academic Registry
            </div>
            <div className="space-y-0.5">
              {registryItems.map((item) => {
                const Icon = item.icon;
                const isActive = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => handleNavClick(item.id)}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition-all group cursor-pointer ${
                      isActive
                        ? 'bg-elevated text-text-primary border border-border shadow-sm'
                        : 'text-text-muted hover:text-text-primary hover:bg-elevated/60 border border-transparent'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <Icon
                        className={`w-4 h-4 transition-colors ${
                          isActive ? 'text-accent' : 'text-text-muted group-hover:text-text-primary'
                        }`}
                      />
                      <span>{item.label}</span>
                    </div>
                    <span
                      className={`text-[9px] font-mono px-1.5 py-0.2 rounded border ${
                        isActive
                          ? 'bg-border text-text-primary border-border font-semibold'
                          : 'bg-elevated text-text-muted border-border group-hover:text-text-primary'
                      }`}
                    >
                      {item.badge}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Section 2: Engine & Analytics */}
          <div>
            <div className="px-2.5 pb-1.5 text-[9px] font-bold text-text-muted uppercase tracking-widest font-mono">
              Compiler & Schema Studio
            </div>
            <div className="space-y-0.5">
              {engineItems.map((item) => {
                const Icon = item.icon;
                const isActive = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => handleNavClick(item.id)}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition-all group cursor-pointer ${
                      isActive
                        ? 'bg-elevated text-text-primary border border-border shadow-sm'
                        : 'text-text-muted hover:text-text-primary hover:bg-elevated/60 border border-transparent'
                    }`}
                  >
                    <div className="flex items-center gap-2.5">
                      <Icon
                        className={`w-4 h-4 transition-colors ${
                          isActive ? 'text-accent' : 'text-text-muted group-hover:text-text-primary'
                        }`}
                      />
                      <span>{item.label}</span>
                    </div>
                    <span
                      className={`text-[9px] font-mono px-1.5 py-0.2 rounded border ${
                        isActive
                          ? 'bg-border text-text-primary border-border font-semibold'
                          : 'bg-elevated text-text-muted border-border group-hover:text-text-primary'
                      }`}
                    >
                      {item.badge}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </nav>

        {/* Footer: Customize & System Guarantee */}
        <div className="p-3 border-t border-border bg-sidebar space-y-2">
          <button
            onClick={onOpenCustomizer}
            className="w-full flex items-center justify-center gap-2 py-2 px-3 text-xs font-mono font-semibold text-text-primary bg-elevated hover:bg-surface border border-border rounded-lg transition-all hover:border-accent cursor-pointer"
          >
            <Sliders className="w-3.5 h-3.5 text-accent" />
            <span>Customize Ledger</span>
          </button>

          <div className="flex items-center justify-between px-1 text-[10px] text-text-muted font-mono">
            <div className="flex items-center gap-1">
              <ShieldCheck className="w-3 h-3 text-accent" />
              <span>Zero N+1 Invariant</span>
            </div>
            <span>SQL Pushdown</span>
          </div>
        </div>
      </aside>
    </>
  );
};
