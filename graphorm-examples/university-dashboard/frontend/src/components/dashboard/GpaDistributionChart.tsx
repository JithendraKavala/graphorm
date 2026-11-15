import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { Student } from '../../types/graphorm';

interface GpaDistributionChartProps {
  students: Student[];
}

export const GpaDistributionChart: React.FC<GpaDistributionChartProps> = ({ students }) => {
  const bins = [
    { range: '< 3.0', label: 'Review', count: 0, color: '#F43F5E' },
    { range: '3.0 - 3.49', label: 'Standard', count: 0, color: 'var(--color-text-muted)' },
    { range: '3.5 - 3.79', label: 'Honors', count: 0, color: 'var(--color-accent)' },
    { range: '3.8 - 3.94', label: 'Magna', count: 0, color: '#F59E0B' },
    { range: '3.95 - 4.0', label: 'Summa', count: 0, color: '#10B981' },
  ];

  students.forEach((s) => {
    const gpa = s.gpa ?? 3.5;
    if (gpa < 3.0) bins[0].count++;
    else if (gpa < 3.5) bins[1].count++;
    else if (gpa < 3.8) bins[2].count++;
    else if (gpa < 3.95) bins[3].count++;
    else bins[4].count++;
  });

  return (
    <div className="p-6 rounded-lg bg-surface border border-border shadow-sm flex flex-col justify-between">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-base text-text-primary tracking-tight">
              Scholastic Merit & GPA Distribution
            </h3>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-accent/10 text-accent border border-accent/30">
              Evaluated Invariants
            </span>
          </div>
          <p className="text-xs text-text-muted mt-1 font-mono">
            Enforced by field-level <code className="text-accent font-mono bg-elevated px-1 py-0.5 rounded border border-border">@auth</code> and validation expressions
          </p>
        </div>
      </div>

      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={bins} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
            <XAxis dataKey="range" stroke="var(--color-text-muted)" fontSize={11} fontFamily="'JetBrains Mono', monospace" tickLine={false} />
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
            <Bar dataKey="count" radius={[4, 4, 0, 0]}>
              {bins.map((entry, index) => (
                <Cell key={`gpa-cell-${index}`} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
