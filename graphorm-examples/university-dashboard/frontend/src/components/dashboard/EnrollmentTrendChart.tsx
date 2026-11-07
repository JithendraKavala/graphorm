import React from 'react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

const trendData = [
  { month: 'Jan', enrollments: 32, completions: 28 },
  { month: 'Feb', enrollments: 45, completions: 38 },
  { month: 'Mar', enrollments: 62, completions: 50 },
  { month: 'Apr', enrollments: 78, completions: 65 },
  { month: 'May', enrollments: 95, completions: 82 },
  { month: 'Jun', enrollments: 88, completions: 80 },
  { month: 'Jul', enrollments: 110, completions: 98 },
  { month: 'Aug', enrollments: 140, completions: 120 },
  { month: 'Sep', enrollments: 185, completions: 155 },
  { month: 'Oct', enrollments: 195, completions: 172 },
  { month: 'Nov', enrollments: 210, completions: 188 },
];

export const EnrollmentTrendChart: React.FC = () => {
  return (
    <div className="p-6 rounded-lg bg-[#161B22] border border-[#30363D] shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="font-bold text-base text-[#F0F6FC] tracking-tight">
              Matriculation Velocity & Credit Progression
            </h3>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/30">
              Aggregated Stream
            </span>
          </div>
          <p className="text-xs text-[#8B949E] mt-1 font-mono">
            Semester volume compiled natively inside PostgreSQL via GraphORM query engine
          </p>
        </div>

        <div className="flex items-center gap-4 text-xs font-mono">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-sky-400" />
            <span className="text-[#F0F6FC] font-medium text-[11px]">New Enrollments</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
            <span className="text-[#F0F6FC] font-medium text-[11px]">Units Conferred</span>
          </div>
        </div>
      </div>

      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={trendData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="skyAreaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#38BDF8" stopOpacity={0.35} />
                <stop offset="95%" stopColor="#38BDF8" stopOpacity={0.0} />
              </linearGradient>
              <linearGradient id="emeraldAreaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10B981" stopOpacity={0.25} />
                <stop offset="95%" stopColor="#10B981" stopOpacity={0.0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#21262D" vertical={false} />
            <XAxis dataKey="month" stroke="#8B949E" fontSize={11} fontFamily="'JetBrains Mono', monospace" tickLine={false} />
            <YAxis stroke="#8B949E" fontSize={11} fontFamily="'JetBrains Mono', monospace" tickLine={false} axisLine={false} />
            <Tooltip
              contentStyle={{
                backgroundColor: '#161B22',
                borderColor: '#30363D',
                borderRadius: '8px',
                color: '#F0F6FC',
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: '12px',
                boxShadow: '0 8px 24px rgba(0, 0, 0, 0.6)',
              }}
            />
            <Area
              type="monotone"
              dataKey="enrollments"
              stroke="#38BDF8"
              strokeWidth={2.5}
              fillOpacity={1}
              fill="url(#skyAreaGrad)"
            />
            <Area
              type="monotone"
              dataKey="completions"
              stroke="#10B981"
              strokeWidth={2}
              fillOpacity={1}
              fill="url(#emeraldAreaGrad)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
