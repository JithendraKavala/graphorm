import React, { useState, useRef, useEffect } from 'react';
import {
  Menu,
  RefreshCw,
  Plus,
  Shield,
  ChevronRight,
  ChevronDown,
  GraduationCap,
  Sparkles,
  Check,
  Lock,
  Unlock,
  KeyRound,
  Info,
} from 'lucide-react';
import {
  ActiveTab,
  DataSourceMode,
  AcademicRole,
  ROLE_PROFILES,
} from '../../types/graphorm';

interface HeaderProps {
  activeTab: ActiveTab;
  dataSourceMode: DataSourceMode;
  currentRole: AcademicRole;
  onRoleChange: (role: AcademicRole) => void;
  onRefresh: () => void;
  isRefreshing: boolean;
  onQuickAction: (action: string) => void;
  onToggleMobileSidebar: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  dataSourceMode,
  currentRole,
  onRoleChange,
  onRefresh,
  isRefreshing,
  onQuickAction,
  onToggleMobileSidebar,
}) => {
  const [isRoleMenuOpen, setIsRoleMenuOpen] = useState(false);
  const [showMatrixModal, setShowMatrixModal] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsRoleMenuOpen(false);
      }
    };
    if (isRoleMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isRoleMenuOpen]);

  const activeProfile = ROLE_PROFILES[currentRole];

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

  const handleSelectRole = (role: AcademicRole) => {
    onRoleChange(role);
    setIsRoleMenuOpen(false);
  };

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

        {/* Interactive Security Role Dropdown Menu */}
        <div className="relative" ref={menuRef}>
          <button
            onClick={() => setIsRoleMenuOpen(!isRoleMenuOpen)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs font-mono transition-all cursor-pointer shadow-sm ${activeProfile.badgeColor} hover:brightness-110`}
            title="Click to switch security roles and observe @auth field level authorization"
            aria-haspopup="true"
            aria-expanded={isRoleMenuOpen}
          >
            <Shield className="w-3.5 h-3.5" />
            <span className="font-semibold">{activeProfile.label}</span>
            <ChevronDown
              className={`w-3.5 h-3.5 transition-transform duration-200 ${
                isRoleMenuOpen ? 'rotate-180' : ''
              }`}
            />
          </button>

          {/* Role Switcher Dropdown */}
          {isRoleMenuOpen && (
            <div className="absolute right-0 mt-2 w-80 rounded-xl bg-[#161B22] border border-[#30363D] shadow-2xl p-2 z-50 animate-in fade-in slide-in-from-top-2 duration-150">
              <div className="px-3 py-2 border-b border-[#30363D] mb-1.5 flex items-center justify-between">
                <div>
                  <div className="text-xs font-bold text-[#F0F6FC] flex items-center gap-1.5">
                    <KeyRound className="w-3.5 h-3.5 text-sky-400" />
                    <span>Switch Role & @auth Identity</span>
                  </div>
                  <p className="text-[10px] text-[#8B949E] mt-0.5">
                    Demonstrates Spring Security & GraphORM field masking
                  </p>
                </div>
                <button
                  onClick={() => {
                    setShowMatrixModal(true);
                    setIsRoleMenuOpen(false);
                  }}
                  className="p-1 text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D] rounded text-[10px] flex items-center gap-1 cursor-pointer"
                  title="View full permissions matrix"
                >
                  <Info className="w-3.5 h-3.5 text-sky-400" />
                </button>
              </div>

              <div className="space-y-1">
                {(Object.keys(ROLE_PROFILES) as AcademicRole[]).map((roleKey) => {
                  const profile = ROLE_PROFILES[roleKey];
                  const isCurrent = roleKey === currentRole;
                  return (
                    <button
                      key={roleKey}
                      onClick={() => handleSelectRole(roleKey)}
                      className={`w-full text-left p-2.5 rounded-lg text-xs transition-colors cursor-pointer flex items-start gap-2.5 ${
                        isCurrent
                          ? 'bg-[#21262D] border border-sky-500/40 text-[#F0F6FC]'
                          : 'hover:bg-[#21262D]/60 text-[#C9D1D9] border border-transparent'
                      }`}
                    >
                      <div className="mt-0.5">
                        {isCurrent ? (
                          <Check className="w-4 h-4 text-sky-400 stroke-[2.5]" />
                        ) : (
                          <div className="w-4 h-4 rounded-full border border-[#30363D]" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-xs text-[#F0F6FC]">
                            {profile.label}
                          </span>
                          <span
                            className={`text-[9px] font-mono px-1.5 py-0.5 rounded border ${profile.badgeColor}`}
                          >
                            {profile.badgeText}
                          </span>
                        </div>
                        <p className="text-[11px] text-[#8B949E] mt-0.5 line-clamp-1">
                          {profile.description}
                        </p>
                        {/* Quick Permission Badges */}
                        <div className="flex items-center gap-1.5 mt-1.5 text-[9px] font-mono">
                          <span
                            className={`inline-flex items-center gap-0.5 px-1 py-0.2 rounded ${
                              profile.permissions.canViewSalary
                                ? 'text-emerald-400 bg-emerald-950/40'
                                : 'text-slate-500 bg-slate-900/50'
                            }`}
                          >
                            {profile.permissions.canViewSalary ? (
                              <Unlock className="w-2.5 h-2.5" />
                            ) : (
                              <Lock className="w-2.5 h-2.5" />
                            )}
                            Salary
                          </span>
                          <span
                            className={`inline-flex items-center gap-0.5 px-1 py-0.2 rounded ${
                              profile.permissions.canViewGpa
                                ? 'text-emerald-400 bg-emerald-950/40'
                                : 'text-slate-500 bg-slate-900/50'
                            }`}
                          >
                            {profile.permissions.canViewGpa ? (
                              <Unlock className="w-2.5 h-2.5" />
                            ) : (
                              <Lock className="w-2.5 h-2.5" />
                            )}
                            GPA
                          </span>
                          <span
                            className={`inline-flex items-center gap-0.5 px-1 py-0.2 rounded ${
                              profile.permissions.canMutate
                                ? 'text-emerald-400 bg-emerald-950/40'
                                : 'text-slate-500 bg-slate-900/50'
                            }`}
                          >
                            {profile.permissions.canMutate ? '✓' : '✗'} Mutate
                          </span>
                        </div>
                      </div>
                    </button>
                  );
                })}
              </div>

              {/* Live Auth Header Preview */}
              <div className="mt-2 pt-2 border-t border-[#30363D] px-2.5 pb-1">
                <div className="text-[10px] text-[#8B949E] font-mono flex items-center justify-between">
                  <span>Basic Auth Header:</span>
                  <span className="text-sky-400 font-semibold truncate max-w-[140px]">
                    {activeProfile.authHeaderValue || '(None / Public)'}
                  </span>
                </div>
              </div>
            </div>
          )}
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
          disabled={!activeProfile.permissions.canMutate}
          className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg font-mono font-bold text-xs shadow-sm transition-all ${
            activeProfile.permissions.canMutate
              ? 'bg-sky-500 hover:bg-sky-400 text-[#0D1117] cursor-pointer'
              : 'bg-[#21262D] text-[#8B949E] opacity-60 cursor-not-allowed border border-[#30363D]'
          }`}
          title={
            activeProfile.permissions.canMutate
              ? 'Quickly enroll/matriculate a new scholar'
              : 'Mutations require ADMIN or INSTRUCTOR privilege (@auth)'
          }
        >
          <Plus className="w-3.5 h-3.5 stroke-[3]" />
          <span>Matriculate</span>
        </button>
      </div>

      {/* Permissions Matrix Modal */}
      {showMatrixModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[#161B22] border border-[#30363D] rounded-2xl max-w-lg w-full p-6 shadow-2xl text-xs space-y-4">
            <div className="flex items-center justify-between border-b border-[#30363D] pb-3">
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-sky-400" />
                <h3 className="font-bold text-sm text-[#F0F6FC]">
                  GraphORM Field-Level Authorization Matrix (@auth)
                </h3>
              </div>
              <button
                onClick={() => setShowMatrixModal(false)}
                className="text-[#8B949E] hover:text-[#F0F6FC] text-sm font-bold"
              >
                ✕
              </button>
            </div>

            <p className="text-[#8B949E] text-xs">
              GraphORM compiles <code className="text-sky-400">@auth(roles: [...])</code> directives from <code className="text-sky-400">schema.graphorm</code> directly into GraphQL execution hooks backed by Spring Security:
            </p>

            <table className="w-full text-left border-collapse font-mono text-[11px]">
              <thead>
                <tr className="border-b border-[#30363D] text-[#8B949E]">
                  <th className="py-1.5">Role</th>
                  <th className="py-1.5">Salary</th>
                  <th className="py-1.5">GPA</th>
                  <th className="py-1.5">Grade</th>
                  <th className="py-1.5">Mutations</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#30363D]/60">
                <tr>
                  <td className="py-2 text-emerald-400 font-bold">ADMIN</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                </tr>
                <tr>
                  <td className="py-2 text-sky-400 font-bold">INSTRUCTOR</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                  <td className="py-2 text-emerald-400">✓ Full</td>
                  <td className="py-2 text-emerald-400">✓ Allowed</td>
                </tr>
                <tr>
                  <td className="py-2 text-purple-400 font-bold">STUDENT</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                  <td className="py-2 text-rose-400">🔒 Masked</td>
                  <td className="py-2 text-rose-400">🔒 Masked</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                </tr>
                <tr>
                  <td className="py-2 text-slate-400 font-bold">GUEST</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                  <td className="py-2 text-rose-400">🔒 Denied</td>
                </tr>
              </tbody>
            </table>

            <div className="pt-2 flex justify-end">
              <button
                onClick={() => setShowMatrixModal(false)}
                className="px-4 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-[#0D1117] font-bold text-xs cursor-pointer"
              >
                Got It
              </button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
