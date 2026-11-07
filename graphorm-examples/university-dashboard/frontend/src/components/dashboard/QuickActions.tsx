import React from 'react';
import { UserPlus, BookPlus, Building, GraduationCap, Code2, Terminal } from 'lucide-react';

interface QuickActionsProps {
  onAction: (action: string) => void;
}

export const QuickActions: React.FC<QuickActionsProps> = ({ onAction }) => {
  const actions = [
    {
      id: 'student',
      title: 'Matriculate Scholar',
      desc: 'Admissions & department declaration',
      icon: UserPlus,
      iconColor: 'text-sky-400',
    },
    {
      id: 'course',
      title: 'Course Offering',
      desc: 'Define curriculum syllabus & units',
      icon: BookPlus,
      iconColor: 'text-emerald-400',
    },
    {
      id: 'enrollment',
      title: 'Course Enrollment',
      desc: 'Register semester & award credits',
      icon: GraduationCap,
      iconColor: 'text-sky-400',
    },
    {
      id: 'department',
      title: 'Faculties & Chairs',
      desc: 'Browse schools & departmental staff',
      icon: Building,
      iconColor: 'text-amber-400',
    },
    {
      id: 'schema',
      title: 'Schema Grammar',
      desc: 'Inspect GraphORM model AST & views',
      icon: Code2,
      iconColor: 'text-sky-400',
    },
    {
      id: 'graphql',
      title: 'GraphQL Studio',
      desc: 'Execute real-time query ASTs',
      icon: Terminal,
      iconColor: 'text-emerald-400',
    },
  ];

  return (
    <div className="p-6 rounded-lg bg-[#161B22] border border-[#30363D] shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="font-bold text-base text-[#F0F6FC] tracking-tight">
            Operational Registry Shortcuts
          </h3>
          <p className="text-xs text-[#8B949E] mt-0.5 font-mono">
            Direct actions for registrars, faculty leads, and database architects
          </p>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {actions.map((act) => {
          const Icon = act.icon;
          return (
            <button
              key={act.id}
              onClick={() => onAction(act.id)}
              className="p-3.5 rounded-lg border border-[#30363D] bg-[#0D1117] hover:bg-[#21262D] hover:border-slate-500 transition-colors text-left flex flex-col justify-between group cursor-pointer"
            >
              <div className="p-2 rounded-md w-fit bg-[#161B22] border border-[#30363D] transition-colors">
                <Icon className={`w-4 h-4 ${act.iconColor}`} />
              </div>
              <div className="mt-3">
                <p className="text-xs font-semibold text-[#F0F6FC] group-hover:text-white transition-colors">
                  {act.title}
                </p>
                <p className="text-[11px] text-[#8B949E] mt-0.5 line-clamp-1">{act.desc}</p>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};
