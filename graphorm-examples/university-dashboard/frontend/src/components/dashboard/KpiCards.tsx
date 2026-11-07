import React from 'react';
import { Users, BookOpen, Building2, Award, TrendingUp } from 'lucide-react';
import { Student, Course, Department, Enrollment } from '../../types/graphorm';

interface KpiCardsProps {
  students: Student[];
  courses: Course[];
  departments: Department[];
  enrollments: Enrollment[];
}

export const KpiCards: React.FC<KpiCardsProps> = ({
  students,
  courses,
  departments,
  enrollments,
}) => {
  // Compute average GPA
  const validGpas = students.filter((s) => s.gpa != null).map((s) => s.gpa as number);
  const avgGpa = validGpas.length > 0
    ? (validGpas.reduce((a, b) => a + b, 0) / validGpas.length).toFixed(2)
    : '0.00';

  const honorsCount = students.filter((s) => (s.gpa ?? 0) >= 3.8).length;
  const honorsPercent = students.length > 0
    ? Math.round((honorsCount / students.length) * 100)
    : 0;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {/* Card 1: Matriculated Scholars */}
      <div className="p-5 rounded-lg bg-[#161B22] border border-[#30363D] hover:border-slate-500 transition-colors shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#8B949E] font-mono">
            Matriculated Scholars
          </span>
          <div className="p-2 rounded-md bg-sky-500/10 text-sky-400 border border-sky-500/25">
            <Users className="w-4 h-4" />
          </div>
        </div>

        <div className="mt-3 flex items-baseline gap-2">
          <span className="font-mono text-3xl font-bold text-[#F0F6FC] tracking-tight tabular-nums">
            {students.length}
          </span>
          <span className="text-xs font-mono text-emerald-400 flex items-center font-medium">
            <TrendingUp className="w-3 h-3 mr-0.5" /> +14.2%
          </span>
        </div>

        <div className="mt-2 pt-2 border-t border-[#30363D] flex items-center justify-between text-[11px] text-[#8B949E]">
          <span>Active in directory</span>
          <span className="font-mono text-sky-400 font-semibold">{departments.length} Faculties</span>
        </div>
      </div>

      {/* Card 2: Curriculum Offerings */}
      <div className="p-5 rounded-lg bg-[#161B22] border border-[#30363D] hover:border-slate-500 transition-colors shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#8B949E] font-mono">
            Curriculum Courses
          </span>
          <div className="p-2 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/25">
            <BookOpen className="w-4 h-4" />
          </div>
        </div>

        <div className="mt-3 flex items-baseline gap-2">
          <span className="font-mono text-3xl font-bold text-[#F0F6FC] tracking-tight tabular-nums">
            {courses.length}
          </span>
          <span className="text-xs font-mono text-[#8B949E]">
            Units Offered
          </span>
        </div>

        <div className="mt-2 pt-2 border-t border-[#30363D] flex items-center justify-between text-[11px] text-[#8B949E]">
          <span>Total enrollments</span>
          <span className="font-mono text-emerald-400 font-semibold">{enrollments.length} Registrations</span>
        </div>
      </div>

      {/* Card 3: Academic Faculties */}
      <div className="p-5 rounded-lg bg-[#161B22] border border-[#30363D] hover:border-slate-500 transition-colors shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#8B949E] font-mono">
            Academic Faculties
          </span>
          <div className="p-2 rounded-md bg-amber-500/10 text-amber-400 border border-amber-500/25">
            <Building2 className="w-4 h-4 text-amber-400" />
          </div>
        </div>

        <div className="mt-3 flex items-baseline gap-2">
          <span className="font-mono text-3xl font-bold text-[#F0F6FC] tracking-tight tabular-nums">
            {departments.length}
          </span>
          <span className="text-xs text-emerald-400 font-medium font-mono">100% Accredited</span>
        </div>

        <div className="mt-2 pt-2 border-t border-[#30363D] flex items-center justify-between text-[11px] text-[#8B949E]">
          <span>Dean & Faculty chairs</span>
          <span className="font-mono text-amber-400 font-semibold">Stage 1 Views</span>
        </div>
      </div>

      {/* Card 4: Mean GPA & Honors */}
      <div className="p-5 rounded-lg bg-[#161B22] border border-[#30363D] hover:border-slate-500 transition-colors shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#8B949E] font-mono">
            Cumulative Mean GPA
          </span>
          <div className="p-2 rounded-md bg-sky-500/10 text-sky-400 border border-sky-500/25">
            <Award className="w-4 h-4" />
          </div>
        </div>

        <div className="mt-3 flex items-baseline gap-2">
          <span className="font-mono text-3xl font-bold text-[#F0F6FC] tracking-tight tabular-nums">
            {avgGpa}
          </span>
          <span className="text-xs font-mono text-[#8B949E]">
            / 4.00
          </span>
        </div>

        <div className="mt-2 pt-2 border-t border-[#30363D] flex items-center justify-between text-[11px] text-[#8B949E]">
          <span>Dean's Honor List</span>
          <span className="font-mono text-emerald-400 font-semibold">{honorsPercent}% of Scholars</span>
        </div>
      </div>
    </div>
  );
};
