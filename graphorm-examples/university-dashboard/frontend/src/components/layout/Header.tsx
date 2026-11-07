import React from 'react';
import {
  Menu,
  RefreshCw,
  Plus,
  Shield,
  ChevronRight,
  GraduationCap,
  Sparkles,
} from 'lucide-react';
import { ActiveTab, DataSourceMode } from '../../types/graphorm';

interface HeaderProps {
  activeTab: ActiveTab;
  dataSourceMode: DataSourceMode;
  onRefresh: () => void;
  isRefreshing: boolean;
  onQuickAction: (action: string) => void;
  onToggleMobileSidebar: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  dataSourceMode,
  onRefresh,
  isRefreshing,
  onQuickAction,
  onToggleMobileSidebar,
}) => {
  const getTabMeta = (tab: ActiveTab) => {
    switch (tab) {
      case 'landing':
        return { title: 'Product Showcase', section: 'GraphORM Engine', desc: 'Type-Safe ORM & GraphQL Compiler Overview' };
      case 'dashboard':
        return { title: 'Executive Overview', section: 'Academic Registry', desc: 'Institutional telemetry & semester performance' };
      case 'students':
        return { title: 'Student Directory', section: 'Admissions & Roster', desc: 'Scholars, cumulative GPA & academic status' };
      case 'departments':
        return { title: 'Academic Faculties', section: 'Schools & Chairs', desc: 'Departmental divisions and faculty curriculum' };
      case 'courses':
        return { title: 'Curriculum Catalog', section: 'Courses & Credits', desc: 'Accredited offerings & professor assignments' };
      case 'enrollments':
        return { title: 'Academic Ledger', section: 'Registrar Records', desc: 'Course enrollments, letter grades & grade points' };
      case 'analytics':
        return { title: 'View Models & Analytics', section: 'GraphORM Architecture', desc: 'Stage 1 Physical View vs Stage 2 Virtual View' };
      case 'schema-explorer':
        return { title: 'Schema Engine & AST', section: 'Prisma-Inspired DSL', desc: 'Grammar directives, validation & metadata' };
      case 'graphql-playground':
        return { title: 'Interactive GraphQL Studio', section: 'Runtime Engine', desc: 'Execute live queries against GraphORM DataLoaders' };
      default:
        return { title: 'University Portal', section: 'GraphORM System', desc: 'Academic Management System' };
    }
  };

  const meta = getTabMeta(activeTab);

  return (
    <header className="h-16 border-b border-[#30363D] bg-[#161B22]/95 backdrop-blur-md px-4 sm:px-8 flex items-center justify-between sticky top-0 z-30 select-none">
      {/* Left: Mobile Toggle & Breadcrumbs */}
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleMobileSidebar}
          className="lg:hidden p-2 text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] rounded-lg transition-colors cursor-pointer"
          aria-label="Toggle navigation drawer"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex flex-col">
          <div className="flex items-center gap-2 text-xs">
            <span className="text-[#8B949E] font-mono text-[10px] tracking-wider uppercase">
              {meta.section}
            </span>
            <ChevronRight className="w-3 h-3 text-[#8B949E]" />
            <h1 className="font-bold text-sm tracking-tight text-[#F0F6FC]">
              {meta.title}
            </h1>
          </div>
          <span className="hidden md:inline-block text-[11px] text-[#8B949E] font-normal">
            {meta.desc}
          </span>
        </div>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-2.5">
        {/* Switch to Product Showcase Button */}
        <button
          onClick={() => onQuickAction('landing')}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#21262D] hover:bg-slate-700 border border-[#30363D] hover:border-slate-500 text-[#F0F6FC] text-xs font-mono transition-all cursor-pointer shadow-sm"
          title="Switch to GraphORM Official Product Showcase"
        >
          <Sparkles className="w-3.5 h-3.5 text-sky-400" />
          <span className="hidden sm:inline">Product Showcase</span>
        </button>

        {/* Academic Term Seal */}
        <div className="hidden xl:flex items-center gap-1.5 px-3 py-1 rounded-lg bg-[#21262D] border border-[#30363D] text-xs">
          <GraduationCap className="w-3.5 h-3.5 text-sky-400" />
          <span className="text-[#8B949E] font-mono text-[11px]">Term:</span>
          <span className="font-semibold text-[#F0F6FC] font-mono text-xs">Fall 2026</span>
        </div>

        {/* Security Role Badge */}
        <div className="hidden sm:flex items-center gap-1.5 px-3 py-1 rounded-lg bg-[#21262D] border border-[#30363D] text-xs">
          <Shield className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-[#8B949E] font-mono text-[11px]">Role:</span>
          <span className="font-semibold text-[#F0F6FC] font-mono text-xs">Dean / Admin</span>
        </div>

        {/* Active Engine Badge */}
        <div className="hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[#21262D] border border-[#30363D] text-xs">
          <span className="text-[10px] text-[#8B949E] font-mono">Engine:</span>
          <span
            className={`font-mono text-[11px] font-semibold uppercase ${
              dataSourceMode === 'graphql' ? 'text-sky-400' : 'text-emerald-400'
            }`}
          >
            {dataSourceMode}
          </span>
        </div>

        {/* Refresh Sync Button */}
        <button
          onClick={onRefresh}
          disabled={isRefreshing}
          className="p-2 text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] rounded-lg border border-[#30363D] transition-colors cursor-pointer"
          title="Refresh Data from GraphORM Engine"
        >
          <RefreshCw
            className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-sky-400' : ''}`}
          />
        </button>

        {/* Matriculate / Quick Action Button */}
        <button
          onClick={() => onQuickAction('student')}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-[#0D1117] font-mono font-bold text-xs shadow-sm transition-all cursor-pointer"
        >
          <Plus className="w-3.5 h-3.5 stroke-[3]" />
          <span>Matriculate</span>
        </button>
      </div>
    </header>
  );
};
