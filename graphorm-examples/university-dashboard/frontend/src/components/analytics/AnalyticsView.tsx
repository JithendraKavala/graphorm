import React, { useState } from 'react';
import { DepartmentStats, StudentDashboard } from '../../types/graphorm';
import { Badge } from '../common/Badge';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ScatterChart,
  Scatter,
  ZAxis,
} from 'recharts';
import {
  Database,
  Layers,
  Sparkles,
  ShieldCheck,
  CheckCircle2,
  Cpu,
  ArrowRight,
  Code,
} from 'lucide-react';

interface AnalyticsViewProps {
  departmentStats: DepartmentStats[];
  studentDashboards: StudentDashboard[];
}

export const AnalyticsView: React.FC<AnalyticsViewProps> = ({
  departmentStats,
  studentDashboards,
}) => {
  const [activeTab, setActiveTab] = useState<'stage1' | 'stage2'>('stage2');

  const deptChartData = departmentStats.map((d) => ({
    name: d.departmentName,
    students: d.studentCount,
    courses: d.courseCount,
    ratio: d.courseCount > 0 ? (d.studentCount / d.courseCount).toFixed(1) : 0,
  }));

  const studentScatterData = studentDashboards.map((s) => ({
    name: s.studentName,
    dept: s.departmentName || 'General',
    courses: s.enrolledCourses,
    gpa: s.avgGradePoints ?? 3.5,
  }));

  return (
    <div className="space-y-5">
      {/* Editorial Architectural Masthead */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-700/80 shadow-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="p-1.5 rounded-lg bg-gold-500/10 text-gold-400 border border-gold-500/20">
                <Sparkles className="w-4 h-4" />
              </div>
              <h3 className="font-display font-bold text-lg text-white tracking-wide">
                GraphORM Schema-Defined View Models
              </h3>
            </div>
            <p className="text-xs text-slate-400 mt-1.5 max-w-2xl leading-relaxed">
              Demonstrating compile-time type safety, zero client-side sorting overhead, and strict read-only immutability across physical SQL views and GraphORM virtual aggregated views.
            </p>
          </div>

          {/* View Model Stage Selector */}
          <div className="flex items-center gap-1.5 bg-ink-900 p-1 rounded-xl border border-ink-750 self-start md:self-auto shadow-sm">
            <button
              onClick={() => setActiveTab('stage2')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
                activeTab === 'stage2'
                  ? 'bg-gold-600 text-ink-950 font-bold shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Cpu className="w-3.5 h-3.5" />
              <span>Virtual Aggregated View (Stage 2)</span>
            </button>
            <button
              onClick={() => setActiveTab('stage1')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
                activeTab === 'stage1'
                  ? 'bg-cobalt-600 text-white font-bold shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Database className="w-3.5 h-3.5" />
              <span>Physical SQL View (Stage 1)</span>
            </button>
          </div>
        </div>
      </div>

      {activeTab === 'stage2' ? (
        /* ================= STAGE 2: VIRTUAL AGGREGATED VIEW ================= */
        <div className="space-y-4">
          {/* Architecture Card */}
          <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
            <div className="flex items-center gap-3.5">
              <div className="p-2.5 rounded-xl bg-gold-500/10 border border-gold-500/20 text-gold-400">
                <Layers className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="font-display font-bold text-sm text-white">
                    view StudentDashboard (Stage 2)
                  </h4>
                  <Badge variant="gold" size="sm">
                    Single SQL Query Pushdown
                  </Badge>
                  <Badge variant="slate" size="sm">
                    Strict Read-Only
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 mt-0.5">
                  Complex relational aggregates computed entirely inside PostgreSQL. Zero memory overhead in Java runtime.
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3 text-xs text-slate-400 font-mono">
              <div className="flex items-center gap-1.5">
                <CheckCircle2 className="w-3.5 h-3.5 text-laurel-400" />
                <span>Zero N+1 Queries</span>
              </div>
              <div className="flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-gold-400" />
                <span>Immutable Projection</span>
              </div>
            </div>
          </div>

          {/* Visual Analytics Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Student Dashboards Data Table */}
            <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm flex flex-col">
              <div className="px-5 py-3.5 bg-ink-900/80 border-b border-ink-750 flex items-center justify-between">
                <div>
                  <h4 className="font-display font-bold text-xs uppercase tracking-wider text-white">
                    Scholar Load & Conferred Grade Point Averages
                  </h4>
                  <p className="text-[11px] text-slate-400 font-mono">
                    Projected via view StudentDashboard
                  </p>
                </div>
                <span className="font-mono text-xs text-gold-300 font-bold">
                  {studentDashboards.length} Rows
                </span>
              </div>
              <div className="overflow-x-auto max-h-[360px]">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-ink-900/90 border-b border-ink-750 text-[10px] uppercase tracking-wider text-slate-400 font-mono sticky top-0">
                    <tr>
                      <th className="py-2.5 px-4">Scholar</th>
                      <th className="py-2.5 px-4">Faculty Division</th>
                      <th className="py-2.5 px-4 text-center">Courses</th>
                      <th className="py-2.5 px-4 text-right">Computed GPA</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-750/50">
                    {studentDashboards.map((s) => (
                      <tr key={s.studentId} className="hover:bg-ink-800/40 transition-colors">
                        <td className="py-3 px-4 font-semibold text-white">
                          {s.studentName}
                        </td>
                        <td className="py-3 px-4 text-slate-400">
                          {s.departmentName || 'General Studies'}
                        </td>
                        <td className="py-3 px-4 text-center font-mono">
                          <span className="px-2 py-0.5 rounded bg-cobalt-500/15 text-cobalt-300 border border-cobalt-500/30 font-semibold tabular-nums">
                            {s.enrolledCourses}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-gold-300 tabular-nums">
                          {s.avgGradePoints != null ? s.avgGradePoints.toFixed(2) : '--'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Scatter Correlation Chart */}
            <div className="p-6 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col justify-between">
              <div>
                <h4 className="font-display font-bold text-base text-white tracking-wide">
                  Course Load vs Conferred Merit Points
                </h4>
                <p className="text-xs text-slate-400 mt-0.5">
                  Correlation of enrolled courses against evaluated grade points
                </p>
              </div>

              <div className="h-60 w-full mt-3">
                <ResponsiveContainer width="100%" height="100%">
                  <ScatterChart margin={{ top: 20, right: 20, bottom: 10, left: -20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#162238" />
                    <XAxis
                      type="number"
                      dataKey="courses"
                      name="Courses"
                      stroke="#64748b"
                      fontSize={11}
                      domain={[0, 4]}
                    />
                    <YAxis
                      type="number"
                      dataKey="gpa"
                      name="GPA"
                      stroke="#64748b"
                      fontSize={11}
                      domain={[2.5, 4.0]}
                    />
                    <ZAxis range={[60, 200]} />
                    <Tooltip
                      cursor={{ strokeDasharray: '3 3' }}
                      contentStyle={{
                        backgroundColor: '#0c1320',
                        borderColor: '#1d2c47',
                        borderRadius: '12px',
                        color: '#f8fafc',
                        fontSize: '12px',
                        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.6)',
                      }}
                    />
                    <Scatter name="Scholars" data={studentScatterData} fill="#d4af37" />
                  </ScatterChart>
                </ResponsiveContainer>
              </div>

              <div className="pt-3 border-t border-ink-750/70 flex items-center justify-between text-xs text-slate-400 font-mono">
                <span>SQL: ROUND(AVG(e.grade_points), 2)</span>
                <span className="text-gold-300 font-bold">100% Engine Pushdown</span>
              </div>
            </div>
          </div>

          {/* Schema Snippet */}
          <div className="p-5 rounded-2xl bg-ink-900 border border-ink-750 font-mono text-xs shadow-sm">
            <div className="flex items-center justify-between text-slate-400 mb-2 font-sans font-medium text-xs">
              <span className="font-mono text-slate-300">schema.graphorm View Model Definition</span>
              <span className="text-gold-300 font-mono">Stage 2 Virtual View</span>
            </div>
            <pre className="text-gold-300 overflow-x-auto p-3.5 rounded-xl bg-ink-950/80 border border-ink-750/70 leading-relaxed">
{`view StudentDashboard {
  studentId       Int     @map("s.id")
  studentName     String  @map("s.name")
  departmentName  String? @map("d.dept_name")
  enrolledCourses Int     @aggregate(count: "e.id")
  avgGradePoints  Float?  @expression("ROUND(AVG(e.grade_points)::numeric, 2)")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
  @@groupBy(["s.id", "s.name", "d.dept_name"])
}`}
            </pre>
          </div>
        </div>
      ) : (
        /* ================= STAGE 1: PHYSICAL VIEW ================= */
        <div className="space-y-4">
          <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
            <div className="flex items-center gap-3.5">
              <div className="p-2.5 rounded-xl bg-cobalt-500/10 border border-cobalt-500/20 text-cobalt-400">
                <Database className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="font-display font-bold text-sm text-white">
                    view DepartmentStats (Stage 1)
                  </h4>
                  <Badge variant="sky" size="sm">
                    PostgreSQL Physical View
                  </Badge>
                  <Badge variant="slate" size="sm">
                    v_department_stats
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 mt-0.5">
                  Physical SQL View mapped directly into GraphORM metadata repository and GraphQL resolver tree.
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3 text-xs text-slate-400 font-mono">
              <span className="text-cobalt-300">db.departmentStats().findMany()</span>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Table */}
            <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm">
              <div className="px-5 py-3.5 bg-ink-900/80 border-b border-ink-750 flex items-center justify-between">
                <div>
                  <h4 className="font-display font-bold text-xs uppercase tracking-wider text-white">
                    Faculty Capacity Ledger
                  </h4>
                  <p className="text-[11px] text-slate-400 font-mono">
                    Sourced from physical view v_department_stats
                  </p>
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-ink-900/90 border-b border-ink-750 text-[10px] uppercase tracking-wider text-slate-400 font-mono">
                    <tr>
                      <th className="py-2.5 px-4">Academic Faculty</th>
                      <th className="py-2.5 px-4 text-center">Scholars</th>
                      <th className="py-2.5 px-4 text-center">Courses</th>
                      <th className="py-2.5 px-4 text-right">Scholar/Course Ratio</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-750/50">
                    {departmentStats.map((d) => (
                      <tr key={d.id} className="hover:bg-ink-800/40 transition-colors">
                        <td className="py-3 px-4 font-semibold text-white">
                          {d.departmentName}
                        </td>
                        <td className="py-3 px-4 text-center font-mono text-gold-300 font-semibold tabular-nums">
                          {d.studentCount}
                        </td>
                        <td className="py-3 px-4 text-center font-mono text-cobalt-300 font-semibold tabular-nums">
                          {d.courseCount}
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-slate-300 tabular-nums">
                          {d.courseCount > 0 ? (d.studentCount / d.courseCount).toFixed(1) : 0}:1
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Bar Chart */}
            <div className="p-6 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col justify-between">
              <div>
                <h4 className="font-display font-bold text-base text-white tracking-wide">
                  Faculty Capacity Comparison
                </h4>
                <p className="text-xs text-slate-400 mt-0.5 mb-4">
                  Enrolled scholars vs curriculum course offerings by faculty
                </p>
              </div>

              <div className="h-60 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={deptChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#162238" vertical={false} />
                    <XAxis dataKey="name" stroke="#64748b" fontSize={11} tickLine={false} />
                    <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#0c1320',
                        borderColor: '#1d2c47',
                        borderRadius: '12px',
                        color: '#f8fafc',
                        fontSize: '12px',
                        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.6)',
                      }}
                    />
                    <Bar dataKey="students" fill="#d4af37" radius={[4, 4, 0, 0]} name="Scholars" />
                    <Bar dataKey="courses" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Courses" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* SQL Snippet */}
          <div className="p-5 rounded-2xl bg-ink-900 border border-ink-750 font-mono text-xs shadow-sm">
            <div className="flex items-center justify-between text-slate-400 mb-2 font-sans font-medium text-xs">
              <span className="font-mono text-slate-300">Database DDL Physical View (schema.sql)</span>
              <span className="text-cobalt-400 font-mono">PostgreSQL 16 Engine</span>
            </div>
            <pre className="text-cobalt-300 overflow-x-auto p-3.5 rounded-xl bg-ink-950/80 border border-ink-750/70 leading-relaxed">
{`CREATE OR REPLACE VIEW v_department_stats AS
SELECT
    d.id AS id,
    d.dept_name AS department_name,
    COUNT(DISTINCT s.id)::INT AS student_count,
    COUNT(DISTINCT c.id)::INT AS course_count
FROM departments d
LEFT JOIN students s ON s.department_id = d.id
LEFT JOIN courses c ON c.department_id = d.id
GROUP BY d.id, d.dept_name;`}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
