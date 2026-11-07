import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { DepartmentStats, StudentDashboard } from '../../types/graphorm';
import { api } from '../../services/api';
import { Badge } from '../common/Badge';
import { DataPagination } from '../common/DataPagination';
import { SearchInput } from '../common/SearchInput';
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
  Code,
  Zap,
  ArrowUpDown,
  Loader2,
} from 'lucide-react';

interface AnalyticsViewProps {
  departmentStats: DepartmentStats[];
  studentDashboards: StudentDashboard[];
}

export const AnalyticsView: React.FC<AnalyticsViewProps> = ({
  departmentStats: initialStats = [],
  studentDashboards: initialDashboards = [],
}) => {
  const [departmentStats, setDepartmentStats] = useState<DepartmentStats[]>(initialStats);
  const [activeTab, setActiveTab] = useState<'stage2' | 'stage1' | 'telemetry'>('stage2');

  // Stage 2 Table Pagination, Sort & Filter State
  const [search, setSearch] = useState('');
  const [minCourses, setMinCourses] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [stage2SortBy, setStage2SortBy] = useState<'studentName' | 'departmentName' | 'enrolledCourses' | 'avgGradePoints'>('avgGradePoints');
  const [stage2SortOrder, setStage2SortOrder] = useState<'asc' | 'desc'>('desc');

  // Server-side paginated dashboards state
  const [dashboards, setDashboards] = useState<StudentDashboard[]>(initialDashboards);
  const [totalDashboards, setTotalDashboards] = useState(initialDashboards.length);
  const [totalDashboardPages, setTotalDashboardPages] = useState(1);
  const [isLoadingDashboards, setIsLoadingDashboards] = useState(false);

  // Stage 1 Sort State
  const [stage1SortBy, setStage1SortBy] = useState<'departmentName' | 'studentCount' | 'courseCount'>('departmentName');
  const [stage1SortOrder, setStage1SortOrder] = useState<'asc' | 'desc'>('asc');

  // Live Join Pruning Interactive Simulator State
  const [includeDeptJoin, setIncludeDeptJoin] = useState(true);
  const [includeEnrollmentJoin, setIncludeEnrollmentJoin] = useState(true);

  // Server-side pushdown query for Stage 2 View Model (StudentDashboard)
  const fetchDashboards = useCallback(async () => {
    setIsLoadingDashboards(true);
    try {
      const res = await api.getStudentDashboardsPaginated({
        page: currentPage,
        pageSize,
        search: search.trim() || undefined,
        minCourses: minCourses > 0 ? minCourses : undefined,
        sortBy: stage2SortBy,
        sortOrder: stage2SortOrder,
      });
      setDashboards(res.items);
      setTotalDashboards(res.pagination.totalItems);
      setTotalDashboardPages(res.pagination.totalPages);
      if (res.departmentStats && res.departmentStats.length > 0) {
        setDepartmentStats(res.departmentStats);
      }
    } catch (err) {
      console.error('Failed to fetch student dashboards:', err);
    } finally {
      setIsLoadingDashboards(false);
    }
  }, [currentPage, pageSize, search, minCourses, stage2SortBy, stage2SortOrder]);

  useEffect(() => {
    fetchDashboards();
  }, [fetchDashboards]);

  const toggleStage2Sort = (field: 'studentName' | 'departmentName' | 'enrolledCourses' | 'avgGradePoints') => {
    if (stage2SortBy === field) {
      setStage2SortOrder(stage2SortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setStage2SortBy(field);
      setStage2SortOrder(field === 'avgGradePoints' || field === 'enrolledCourses' ? 'desc' : 'asc');
    }
    setCurrentPage(1);
  };

  const toggleStage1Sort = (field: 'departmentName' | 'studentCount' | 'courseCount') => {
    if (stage1SortBy === field) {
      setStage1SortOrder(stage1SortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setStage1SortBy(field);
      setStage1SortOrder('asc');
    }
  };

  const sortedDepartmentStats = useMemo(() => {
    return [...departmentStats].sort((a, b) => {
      if (stage1SortBy === 'departmentName') {
        return stage1SortOrder === 'asc'
          ? a.departmentName.localeCompare(b.departmentName)
          : b.departmentName.localeCompare(a.departmentName);
      }
      if (stage1SortBy === 'studentCount') {
        return stage1SortOrder === 'asc'
          ? a.studentCount - b.studentCount
          : b.studentCount - a.studentCount;
      }
      return stage1SortOrder === 'asc'
        ? a.courseCount - b.courseCount
        : b.courseCount - a.courseCount;
    });
  }, [departmentStats, stage1SortBy, stage1SortOrder]);

  const deptChartData = departmentStats.map((d) => ({
    name: d.departmentName,
    students: d.studentCount,
    courses: d.courseCount,
    ratio: d.courseCount > 0 ? (d.studentCount / d.courseCount).toFixed(1) : 0,
  }));

  const studentScatterData = (dashboards.length > 0 ? dashboards : initialDashboards).map((s) => ({
    name: s.studentName,
    dept: s.departmentName || 'General',
    courses: s.enrolledCourses,
    gpa: s.avgGradePoints ?? 3.5,
  }));

  // Dynamically generated SQL based on selected projection fields (Join Pruner simulation)
  const dynamicGeneratedSql = useMemo(() => {
    const projections = ['s.id AS student_id', 's.name AS student_name'];
    const joins = [];
    const groupBy = ['s.id', 's.name'];

    if (includeDeptJoin) {
      projections.push('d.dept_name AS department_name');
      joins.push('LEFT JOIN departments d ON s.department_id = d.id');
      groupBy.push('d.dept_name');
    }

    if (includeEnrollmentJoin) {
      projections.push('COUNT(e.id)::INT AS enrolled_courses');
      projections.push('ROUND(AVG(e.grade_points)::numeric, 2) AS avg_grade_points');
      joins.push('LEFT JOIN enrollments e ON s.id = e.student_id');
    }

    const joinClause = joins.length > 0 ? '\n' + joins.join('\n') : '';
    const groupByClause = '\nGROUP BY ' + groupBy.join(', ');

    return `SELECT\n    ${projections.join(',\n    ')}\nFROM students s${joinClause}${groupByClause}\nORDER BY s.id ASC;`;
  }, [includeDeptJoin, includeEnrollmentJoin]);

  return (
    <div className="space-y-5">
      {/* Architectural Masthead */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-700/80 shadow-sm">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <div className="p-1.5 rounded-lg bg-gold-500/10 text-gold-400 border border-gold-500/20">
                <Sparkles className="w-4 h-4" />
              </div>
              <h3 className="font-display font-bold text-lg text-white tracking-wide">
                GraphORM Schema-Defined View Models & Telemetry
              </h3>
            </div>
            <p className="text-xs text-slate-400 mt-1.5 max-w-2xl leading-relaxed">
              Demonstrating compile-time type safety, selection-set join pruning, and strict read-only immutability across physical SQL views and GraphORM virtual aggregated views.
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
              <span>Virtual View (Stage 2)</span>
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
              <span>Physical View (Stage 1)</span>
            </button>
            <button
              onClick={() => setActiveTab('telemetry')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 cursor-pointer ${
                activeTab === 'telemetry'
                  ? 'bg-sky-500 text-ink-950 font-bold shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Zap className="w-3.5 h-3.5" />
              <span>Join Pruner & DataLoader</span>
            </button>
          </div>
        </div>
      </div>

      {activeTab === 'stage2' && (
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

          {/* Filter Bar */}
          <div className="p-3.5 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
            <div className="flex-1 flex flex-wrap items-center gap-3">
              <SearchInput
                value={search}
                onChange={(val) => {
                  setSearch(val);
                  setCurrentPage(1);
                }}
                placeholder="Filter aggregated view by scholar or faculty..."
                className="w-full sm:w-64"
              />
              <div className="flex items-center gap-2 bg-ink-900/80 border border-ink-700/70 rounded-xl px-3 py-1.5">
                <span className="text-slate-400 font-mono text-[11px]">Min Courses:</span>
                <input
                  type="range"
                  min="0"
                  max="4"
                  step="1"
                  value={minCourses}
                  onChange={(e) => {
                    setMinCourses(parseInt(e.target.value));
                    setCurrentPage(1);
                  }}
                  className="w-16 accent-gold-500 cursor-pointer"
                />
                <span className="font-mono text-white text-[11px] font-semibold w-4">
                  {minCourses}
                </span>
              </div>
              <div className="flex items-center gap-1.5 bg-ink-900/80 border border-ink-700/70 rounded-xl px-2.5 py-1.5">
                <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />
                <select
                  value={`${stage2SortBy}-${stage2SortOrder}`}
                  onChange={(e) => {
                    const [f, o] = e.target.value.split('-');
                    setStage2SortBy(f as 'studentName' | 'departmentName' | 'enrolledCourses' | 'avgGradePoints');
                    setStage2SortOrder(o as 'asc' | 'desc');
                    setCurrentPage(1);
                  }}
                  className="bg-transparent text-slate-200 focus:outline-none cursor-pointer text-xs"
                  aria-label="Sort virtual view"
                >
                  <option value="avgGradePoints-desc" className="bg-ink-900 text-slate-200">GPA (High-Low)</option>
                  <option value="avgGradePoints-asc" className="bg-ink-900 text-slate-200">GPA (Low-High)</option>
                  <option value="studentName-asc" className="bg-ink-900 text-slate-200">Scholar (A-Z)</option>
                  <option value="studentName-desc" className="bg-ink-900 text-slate-200">Scholar (Z-A)</option>
                  <option value="enrolledCourses-desc" className="bg-ink-900 text-slate-200">Courses (High-Low)</option>
                  <option value="departmentName-asc" className="bg-ink-900 text-slate-200">Faculty (A-Z)</option>
                </select>
              </div>

              {isLoadingDashboards && (
                <div className="flex items-center gap-1 text-[11px] font-mono text-gold-400 animate-pulse ml-1">
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Computing pushdown...</span>
                </div>
              )}
            </div>
            <span className="text-slate-400 font-mono text-[11px]">
              Showing <strong className="text-white">{dashboards.length}</strong> of {totalDashboards} aggregated view rows
            </span>
          </div>

          {/* Visual Analytics Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Student Dashboards Data Table */}
            <div className="rounded-2xl bg-ink-850/90 border border-ink-700/70 overflow-hidden shadow-sm flex flex-col justify-between">
              <div>
                <div className="px-5 py-3.5 bg-ink-900/80 border-b border-ink-750 flex items-center justify-between">
                  <div>
                    <h4 className="font-display font-bold text-xs uppercase tracking-wider text-white">
                      Aggregated Virtual Ledger
                    </h4>
                    <p className="text-[11px] text-slate-400 font-mono">
                      Compiled via @@from, @@leftJoin & @aggregate
                    </p>
                  </div>
                  <span className="font-mono text-xs text-gold-300 font-bold">
                    {totalDashboards} Records
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs text-slate-300">
                    <thead className="bg-ink-900/90 border-b border-ink-750 text-[10px] uppercase tracking-wider text-slate-400 font-mono">
                      <tr>
                        <th
                          className="py-2.5 px-4 cursor-pointer hover:text-white transition-colors"
                          onClick={() => toggleStage2Sort('studentName')}
                        >
                          <div className="flex items-center gap-1.5">
                            <span>Scholar</span>
                            <ArrowUpDown className="w-3 h-3" />
                          </div>
                        </th>
                        <th
                          className="py-2.5 px-4 cursor-pointer hover:text-white transition-colors"
                          onClick={() => toggleStage2Sort('departmentName')}
                        >
                          <div className="flex items-center gap-1.5">
                            <span>Faculty Division</span>
                            <ArrowUpDown className="w-3 h-3" />
                          </div>
                        </th>
                        <th
                          className="py-2.5 px-4 text-center cursor-pointer hover:text-white transition-colors"
                          onClick={() => toggleStage2Sort('enrolledCourses')}
                        >
                          <div className="flex items-center justify-center gap-1.5">
                            <span>Courses</span>
                            <ArrowUpDown className="w-3 h-3" />
                          </div>
                        </th>
                        <th
                          className="py-2.5 px-4 text-right cursor-pointer hover:text-white transition-colors"
                          onClick={() => toggleStage2Sort('avgGradePoints')}
                        >
                          <div className="flex items-center justify-end gap-1.5">
                            <span>Computed GPA</span>
                            <ArrowUpDown className="w-3 h-3" />
                          </div>
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-750/50">
                      {dashboards.length === 0 ? (
                        <tr>
                          <td colSpan={4} className="py-8 text-center text-slate-400 text-xs">
                            {isLoadingDashboards ? 'Computing virtual view rows...' : 'No virtual records found matching filters.'}
                          </td>
                        </tr>
                      ) : (
                        dashboards.map((s) => (
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
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* DataPagination for Virtual View Table */}
              <DataPagination
                pagination={{
                  currentPage,
                  pageSize,
                  totalItems: totalDashboards,
                  totalPages: totalDashboardPages,
                }}
                onPageChange={setCurrentPage}
                onPageSizeChange={(newSize) => {
                  setPageSize(newSize);
                  setCurrentPage(1);
                }}
                pageSizeOptions={[3, 5, 10]}
                itemLabel="virtual rows"
                className="rounded-t-none border-t border-b-0 border-x-0"
              />
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
      )}

      {activeTab === 'stage1' && (
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
                      <th
                        className="py-2.5 px-4 cursor-pointer hover:text-white transition-colors"
                        onClick={() => toggleStage1Sort('departmentName')}
                      >
                        <div className="flex items-center gap-1.5">
                          <span>Academic Faculty</span>
                          <ArrowUpDown className="w-3 h-3" />
                        </div>
                      </th>
                      <th
                        className="py-2.5 px-4 text-center cursor-pointer hover:text-white transition-colors"
                        onClick={() => toggleStage1Sort('studentCount')}
                      >
                        <div className="flex items-center justify-center gap-1.5">
                          <span>Scholars</span>
                          <ArrowUpDown className="w-3 h-3" />
                        </div>
                      </th>
                      <th
                        className="py-2.5 px-4 text-center cursor-pointer hover:text-white transition-colors"
                        onClick={() => toggleStage1Sort('courseCount')}
                      >
                        <div className="flex items-center justify-center gap-1.5">
                          <span>Courses</span>
                          <ArrowUpDown className="w-3 h-3" />
                        </div>
                      </th>
                      <th className="py-2.5 px-4 text-right">Scholar/Course Ratio</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink-750/50">
                    {sortedDepartmentStats.map((d) => (
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

      {activeTab === 'telemetry' && (
        /* ================= TELEMETRY: JOIN PRUNER & DATALOADER ================= */
        <div className="space-y-4">
          {/* Header */}
          <div className="p-4 rounded-2xl bg-ink-850/90 border border-ink-700/70 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
            <div className="flex items-center gap-3.5">
              <div className="p-2.5 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400">
                <Zap className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="font-display font-bold text-sm text-white">
                    GraphORM Query Engine Optimization Telemetry
                  </h4>
                  <Badge variant="sky" size="sm">
                    Live Join Pruner
                  </Badge>
                  <Badge variant="laurel" size="sm">
                    DataLoader Batching
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 mt-0.5">
                  See how GraphORM optimizes SQL generation by pruning unused table joins and batching relation lookups to eliminate N+1 roundtrips.
                </p>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Interactive Join Pruning Studio */}
            <div className="p-5 rounded-2xl bg-ink-900 border border-ink-750 flex flex-col justify-between shadow-sm">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-display font-bold text-sm text-white flex items-center gap-2">
                    <Code className="w-4 h-4 text-sky-400" />
                    <span>Dynamic SQL Join Pruner Simulator</span>
                  </h4>
                  <Badge variant={!includeDeptJoin || !includeEnrollmentJoin ? 'laurel' : 'slate'} size="sm">
                    {!includeDeptJoin || !includeEnrollmentJoin ? 'Join Pruned!' : 'Full Join Set'}
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 mb-4">
                  Toggle selection-set fields below. When unselected, GraphORM detects that relations are not needed and prunes the SQL <code>LEFT JOIN</code> clause automatically.
                </p>

                {/* Field Checkboxes */}
                <div className="space-y-2 p-3 bg-ink-950/80 rounded-xl border border-ink-750/70 text-xs font-mono">
                  <label className="flex items-center gap-2.5 text-slate-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={true}
                      disabled={true}
                      className="accent-sky-500"
                    />
                    <span>s.id, s.name (Root Table: Student)</span>
                    <span className="text-[10px] text-slate-500 ml-auto font-sans">Required Root</span>
                  </label>
                  <label className="flex items-center gap-2.5 text-sky-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={includeDeptJoin}
                      onChange={(e) => setIncludeDeptJoin(e.target.checked)}
                      className="accent-sky-500"
                    />
                    <span>d.dept_name (Requires Department Join)</span>
                    {!includeDeptJoin && (
                      <span className="text-[10px] text-laurel-400 font-bold ml-auto font-sans">
                        PRUNED FROM SQL
                      </span>
                    )}
                  </label>
                  <label className="flex items-center gap-2.5 text-gold-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={includeEnrollmentJoin}
                      onChange={(e) => setIncludeEnrollmentJoin(e.target.checked)}
                      className="accent-gold-500"
                    />
                    <span>e.enrolled_courses, e.avg_grade_points (Requires Enrollment Join)</span>
                    {!includeEnrollmentJoin && (
                      <span className="text-[10px] text-laurel-400 font-bold ml-auto font-sans">
                        PRUNED FROM SQL
                      </span>
                    )}
                  </label>
                </div>
              </div>

              {/* Dynamic SQL Output */}
              <div className="mt-4 pt-3 border-t border-ink-750/70">
                <span className="text-[10px] font-mono text-slate-400 uppercase tracking-widest block mb-1.5">
                  Compiled PostgreSQL Query:
                </span>
                <pre className="text-sky-300 font-mono text-[11px] p-3 rounded-xl bg-ink-950 border border-ink-750 overflow-x-auto leading-relaxed">
                  {dynamicGeneratedSql}
                </pre>
              </div>
            </div>

            {/* DataLoader Waterfall Visualizer */}
            <div className="p-5 rounded-2xl bg-ink-900 border border-ink-750 flex flex-col justify-between shadow-sm">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="font-display font-bold text-sm text-white flex items-center gap-2">
                    <Zap className="w-4 h-4 text-laurel-400" />
                    <span>DataLoader N+1 Batching Comparison</span>
                  </h4>
                  <Badge variant="laurel" size="sm">
                    88% Fewer Queries
                  </Badge>
                </div>
                <p className="text-xs text-slate-400 mb-4">
                  Comparing traditional JPA/Hibernate multi-query execution against GraphORM <code>BatchRelationDataLoader</code>.
                </p>

                {/* Comparison Visualizer */}
                <div className="space-y-3 font-mono text-xs">
                  {/* Without DataLoader (Traditional) */}
                  <div className="p-3 bg-garnet-950/20 border border-garnet-500/30 rounded-xl">
                    <div className="flex items-center justify-between text-garnet-300 font-bold text-xs mb-1">
                      <span>Traditional ORM (N+1 Problem)</span>
                      <span>9 Queries / 92ms</span>
                    </div>
                    <div className="space-y-1 text-[10px] text-slate-400">
                      <div className="p-1 bg-ink-950 rounded border border-ink-750/50">
                        Q1: SELECT * FROM students;
                      </div>
                      <div className="p-1 bg-garnet-950/40 rounded border border-garnet-500/20 text-garnet-300">
                        Q2–Q9: SELECT * FROM enrollments WHERE student_id = ? (8 separate roundtrips)
                      </div>
                    </div>
                  </div>

                  {/* With GraphORM DataLoader */}
                  <div className="p-3 bg-laurel-950/20 border border-laurel-500/30 rounded-xl">
                    <div className="flex items-center justify-between text-laurel-300 font-bold text-xs mb-1">
                      <span>GraphORM BatchRelationLoader</span>
                      <span>2 Queries / 11ms</span>
                    </div>
                    <div className="space-y-1 text-[10px] text-slate-400">
                      <div className="p-1 bg-ink-950 rounded border border-ink-750/50">
                        Q1: SELECT * FROM students;
                      </div>
                      <div className="p-1 bg-laurel-950/40 rounded border border-laurel-500/20 text-laurel-300">
                        Q2: SELECT * FROM enrollments WHERE student_id IN (1, 2, 3, 4, 5, 6, 7, 8);
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-ink-750/70 flex items-center justify-between text-xs text-slate-400 font-mono">
                <span>Instrumentation: Micrometer</span>
                <span className="text-laurel-300 font-bold">100% Batched Invariant</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
