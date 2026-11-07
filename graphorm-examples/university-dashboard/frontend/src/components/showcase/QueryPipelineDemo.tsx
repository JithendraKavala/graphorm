import React, { useState } from 'react';
import { Database, Code2, Terminal, Filter, Cpu, Zap } from 'lucide-react';

export const QueryPipelineDemo: React.FC = () => {
  const [activeStage, setActiveStage] = useState<1 | 2 | 3 | 4 | 5>(4);

  // Field selection toggles to demonstrate SQL Projection & Join Pruning in real time
  const [includeDepartment, setIncludeDepartment] = useState(true);
  const [includeCourseCount, setIncludeCourseCount] = useState(true);
  const [includeGpa, setIncludeGpa] = useState(true);

  // Compute live SQL based on field selections
  const getGeneratedSql = () => {
    const selectedColumns = ['s.id AS student_id', 's.name AS student_name'];
    const joins = [];
    const groupBy = ['s.id', 's.name'];

    if (includeDepartment) {
      selectedColumns.push('d.dept_name AS department_name');
      joins.push('LEFT JOIN departments d ON s.department_id = d.id');
      groupBy.push('d.dept_name');
    }

    if (includeCourseCount) {
      selectedColumns.push('COUNT(e.id) AS enrolled_courses');
    }

    if (includeGpa) {
      selectedColumns.push('ROUND(AVG(e.grade_points)::numeric, 2) AS avg_gpa');
    }

    if (includeCourseCount || includeGpa) {
      joins.push('LEFT JOIN enrollments e ON s.id = e.student_id');
    }

    const selectClause = selectedColumns.join(',\n    ');
    const joinClause = joins.length > 0 ? '\n' + joins.join('\n') : '';
    const groupByClause = groupBy.length > 0 ? `\nGROUP BY ${groupBy.join(', ')}` : '';

    return `SELECT
    ${selectClause}
FROM students s${joinClause}${groupByClause}
WHERE s.id = ?
LIMIT 1;`;
  };

  // Compute live Result JSON
  const getResultJson = () => {
    const result: Record<string, any> = {
      studentId: 101,
      studentName: 'Alex Rivera',
    };

    if (includeDepartment) {
      result.departmentName = 'Computer Science';
    }
    if (includeCourseCount) {
      result.enrolledCourses = 4;
    }
    if (includeGpa) {
      result.avgGpa = 3.88;
    }

    return JSON.stringify({ data: { studentDashboard: result } }, null, 2);
  };

  const stages = [
    { id: 1, label: '1. Schema DSL', icon: Code2, desc: 'Declarative View Model' },
    { id: 2, label: '2. Java 21 Client', icon: Cpu, desc: 'Type-Safe Criteria' },
    { id: 3, label: '3. GraphQL Query', icon: Filter, desc: 'Dynamic Selection' },
    { id: 4, label: '4. Generated SQL', icon: Database, desc: 'Join & Projection Pruning' },
    { id: 5, label: '5. Result Data', icon: Terminal, desc: 'Hydrated Response' },
  ];

  return (
    <section id="pipeline" className="py-20 bg-[#0D1117] border-t border-[#30363D]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-12">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-[#161B22] border border-sky-500/30 text-sky-400 font-mono text-xs font-semibold">
            <Zap className="w-3.5 h-3.5" />
            <span>Interactive Query Pipeline</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-[#F0F6FC] tracking-tight">
            From Schema to Optimized SQL in Real Time
          </h2>

          <p className="text-[#8B949E] text-sm sm:text-base leading-relaxed">
            Experience how GraphORM compiles a single high-level model into type-safe Java code, GraphQL schemas, and optimal database SQL with zero unnecessary joins.
          </p>
        </div>

        {/* 5-Step Progress Bar Navigation */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 mb-8 font-mono">
          {stages.map((stage) => {
            const Icon = stage.icon;
            const isActive = activeStage === stage.id;
            return (
              <button
                key={stage.id}
                onClick={() => setActiveStage(stage.id as any)}
                className={`p-3 rounded-lg border text-left transition-colors cursor-pointer ${
                  isActive
                    ? 'bg-[#21262D] border-sky-400 text-[#F0F6FC] ring-1 ring-sky-400/50'
                    : 'bg-[#161B22] border-[#30363D] text-[#8B949E] hover:text-[#F0F6FC] hover:bg-[#21262D]'
                }`}
              >
                <div className="flex items-center gap-2">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-sky-400' : 'text-[#8B949E]'}`} />
                  <span className="text-xs font-semibold">{stage.label}</span>
                </div>
                <p className="text-[10px] text-[#6E7681] mt-1 truncate">{stage.desc}</p>
              </button>
            );
          })}
        </div>

        {/* Interactive Optimizer Bar */}
        <div className="mb-6 p-4 rounded-lg bg-[#161B22] border border-[#30363D] flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-2 h-2 rounded-full bg-emerald-400" />
            <span className="text-xs font-mono font-semibold text-[#F0F6FC]">
              Interactive Field Selector (Toggle to observe dynamic SQL pruning):
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-xs font-mono text-[#F0F6FC] bg-[#0D1117] px-3 py-1.5 rounded border border-[#30363D] cursor-pointer hover:border-slate-500 transition-colors">
              <input
                type="checkbox"
                checked={includeDepartment}
                onChange={(e) => setIncludeDepartment(e.target.checked)}
                className="rounded border-[#30363D] bg-[#161B22] text-sky-500 focus:ring-0"
              />
              <span>departmentName</span>
            </label>

            <label className="flex items-center gap-2 text-xs font-mono text-[#F0F6FC] bg-[#0D1117] px-3 py-1.5 rounded border border-[#30363D] cursor-pointer hover:border-slate-500 transition-colors">
              <input
                type="checkbox"
                checked={includeCourseCount}
                onChange={(e) => setIncludeCourseCount(e.target.checked)}
                className="rounded border-[#30363D] bg-[#161B22] text-sky-500 focus:ring-0"
              />
              <span>enrolledCourses (count)</span>
            </label>

            <label className="flex items-center gap-2 text-xs font-mono text-[#F0F6FC] bg-[#0D1117] px-3 py-1.5 rounded border border-[#30363D] cursor-pointer hover:border-slate-500 transition-colors">
              <input
                type="checkbox"
                checked={includeGpa}
                onChange={(e) => setIncludeGpa(e.target.checked)}
                className="rounded border-[#30363D] bg-[#161B22] text-sky-500 focus:ring-0"
              />
              <span>avgGpa (expression)</span>
            </label>
          </div>
        </div>

        {/* Stage Content Window */}
        <div className="rounded-lg bg-[#161B22] border border-[#30363D] overflow-hidden font-mono text-xs shadow-md">
          {/* Editor Header Bar */}
          <div className="bg-[#161B22] px-4 py-3 border-b border-[#30363D] flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-[#30363D] inline-block" />
                <span className="w-2.5 h-2.5 rounded-full bg-[#30363D] inline-block" />
                <span className="w-2.5 h-2.5 rounded-full bg-[#30363D] inline-block" />
              </div>
              <span className="text-[#F0F6FC] font-semibold ml-2">
                {activeStage === 1 && 'src/main/resources/schema.graphorm (View Model Definition)'}
                {activeStage === 2 && 'StudentDashboardService.java (Type-Safe Client API)'}
                {activeStage === 3 && 'GraphQL Request (Selection Set)'}
                {activeStage === 4 && 'PostgreSQL Dynamic Execution (Generated SQL)'}
                {activeStage === 5 && 'HTTP 200 Response (Hydrated JSON)'}
              </span>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-[10px] px-2 py-0.5 rounded bg-[#21262D] text-[#8B949E] border border-[#30363D]">
                {activeStage === 1 && 'ANTLR4 Grammar'}
                {activeStage === 2 && 'JavaPoet Java 21'}
                {activeStage === 3 && 'Spring for GraphQL'}
                {activeStage === 4 && 'PostgresDialect'}
                {activeStage === 5 && 'Dynamic RowMapper'}
              </span>
            </div>
          </div>

          {/* Editor Code Area */}
          <div className="p-5 bg-[#0A0D12] text-[#F0F6FC] overflow-x-auto leading-relaxed min-h-[320px]">
            {activeStage === 1 && (
              <pre className="text-slate-200">
{`// Declarative View Model with Virtual Joins & Aggregations
view StudentDashboard {
  studentId       Int     @map("s.id")
  studentName     String  @map("s.name")
  departmentName  String? @map("d.dept_name")
  enrolledCourses Int     @aggregate(count: "e.id")
  avgGpa          Float?  @expression("ROUND(AVG(e.grade_points)::numeric, 2)")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
  @@groupBy(["s.id", "s.name", "d.dept_name"])
}`}
              </pre>
            )}

            {activeStage === 2 && (
              <pre className="text-slate-200">
{`@Service
public class StudentDashboardService {
    @Autowired
    private GraphOrmClient db;

    public StudentDashboard getStudentMetrics(int studentId) {
        // Fluent, compile-time verified criteria queries
        return db.studentDashboard().findById(studentId)
            .orElseThrow(() -> new RecordNotFoundException("Student not found"));
    }

    public List<StudentDashboard> getTopPerformers() {
        return db.studentDashboard().findMany(q -> q
            .where(f -> f.avgGpa().gte(3.5))
            .orderBy(f -> f.avgGpa().desc())
            .limit(10)
        );
    }
}`}
              </pre>
            )}

            {activeStage === 3 && (
              <pre className="text-slate-200">
{`query GetStudentDashboard {
  studentDashboard(id: 101) {
    studentId
    studentName${includeDepartment ? '\n    departmentName' : ''}${includeCourseCount ? '\n    enrolledCourses' : ''}${includeGpa ? '\n    avgGpa' : ''}
  }
}`}
              </pre>
            )}

            {activeStage === 4 && (
              <div>
                <div className="mb-3 p-2.5 rounded bg-[#161B22] border border-emerald-500/30 text-emerald-300 text-xs flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Zap className="w-4 h-4 text-emerald-400" />
                    <span>
                      {!includeDepartment && (includeCourseCount || includeGpa)
                        ? 'Join Pruning Active: departments table omitted from SQL AST'
                        : includeDepartment && !includeCourseCount && !includeGpa
                        ? 'Join Pruning Active: enrollments table omitted from SQL AST'
                        : !includeDepartment && !includeCourseCount && !includeGpa
                        ? 'Full Pruning Active: Zero joins generated, direct single-table SELECT'
                        : 'Unified Query Planner: Projection & Joins matched to exact GraphQL selection'}
                    </span>
                  </div>
                  <span className="text-[10px] font-mono text-emerald-400">100% Parameterized</span>
                </div>
                <pre className="text-emerald-300 font-semibold">{getGeneratedSql()}</pre>
              </div>
            )}

            {activeStage === 5 && (
              <pre className="text-sky-300">{getResultJson()}</pre>
            )}
          </div>

          {/* Footnote Bar with Controls */}
          <div className="p-3 bg-[#161B22] border-t border-[#30363D] flex items-center justify-between text-xs text-[#8B949E]">
            <div>
              <span>Stage {activeStage} of 5</span>
            </div>

            <div className="flex items-center gap-2">
              <button
                disabled={activeStage === 1}
                onClick={() => setActiveStage((prev) => (prev > 1 ? ((prev - 1) as any) : 1))}
                className="px-3 py-1 rounded bg-[#21262D] hover:bg-slate-700 disabled:opacity-40 text-[#F0F6FC] transition-colors cursor-pointer"
              >
                Previous Step
              </button>

              <button
                disabled={activeStage === 5}
                onClick={() => setActiveStage((prev) => (prev < 5 ? ((prev + 1) as any) : 5))}
                className="px-3 py-1 rounded bg-sky-400 hover:bg-sky-300 disabled:opacity-40 text-slate-950 font-semibold transition-colors cursor-pointer"
              >
                Next Step
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
