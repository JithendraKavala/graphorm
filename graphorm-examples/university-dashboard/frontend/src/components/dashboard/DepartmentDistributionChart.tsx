import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { DepartmentStats } from '../../types/graphorm';

interface DepartmentDistributionChartProps {
  stats: DepartmentStats[];
}

export const DepartmentDistributionChart: React.FC<DepartmentDistributionChartProps> = ({
  stats,
}) => {
  const chartData = stats.map((s) => ({
    name: s.departmentName.length > 15 ? s.departmentName.substring(0, 15) + '…' : s.departmentName,
    fullName: s.departmentName,
    students: s.studentCount,
    courses: s.courseCount,
  }));

  return (
    <div className="p-6 rounded-lg bg-surface border border-border shadow-sm flex flex-col justify-between">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-base text-text-primary tracking-tight">
              Faculty Capacity & Course Allocation
            </h3>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/30">
              Stage 1 View
            </span>
          </div>
          <p className="text-xs text-text-muted mt-1 font-mono">
            Synchronized with PostgreSQL physical view <code className="text-amber-400 font-mono bg-elevated px-1 py-0.5 rounded border border-border">v_department_stats</code>
          </p>
        </div>

        <div className="flex items-center gap-3 text-xs font-mono">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-sm bg-accent" />
            <span className="text-text-primary font-medium text-[11px]">Scholars</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-sm bg-amber-400" />
            <span className="text-text-primary font-medium text-[11px]">Courses</span>
          </div>
        </div>
      </div>

      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
            <XAxis dataKey="name" stroke="var(--color-text-muted)" fontSize={11} fontFamily="'JetBrains Mono', monospace" tickLine={false} />
            <YAxis stroke="var(--color-text-muted)" fontSize={11} fontFamily="'JetBrains Mono', monospace" tickLine={false} axisLine={false} />
            <Tooltip
              contentStyle={{
                backgroundColor: 'var(--color-surface)',
                borderColor: 'var(--color-border)',
                borderRadius: '8px',
                color: 'var(--color-text-primary)',
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: '12px',
                boxShadow: '0 8px 24px rgba(0, 0, 0, 0.25)',
              }}
            />
            <Bar dataKey="students" fill="var(--color-accent)" radius={[4, 4, 0, 0]} name="Students" />
            <Bar dataKey="courses" fill="#F59E0B" radius={[4, 4, 0, 0]} name="Courses" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
