import React, { useState } from 'react';
import { ShieldAlert, Sparkles, Layers, Cpu } from 'lucide-react';

export const ViewModelsDeepDive: React.FC = () => {
  const [activeStrategy, setActiveStrategy] = useState<'virtual' | 'physical'>('virtual');

  return (
    <section id="view-models" className="py-20 bg-[#0D1117] border-t border-[#30363D]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-4 mb-12">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-md bg-[#161B22] border border-amber-500/30 text-amber-400 font-mono text-xs font-semibold">
            <Layers className="w-3.5 h-3.5" />
            <span>First-Class Primitive</span>
          </div>

          <h2 className="text-2xl sm:text-4xl font-bold text-[#F0F6FC] tracking-tight">
            Schema-Defined View Models
          </h2>

          <p className="text-[#8B949E] text-sm sm:text-base leading-relaxed">
            Eliminate custom DTO records, complex multi-table SQL queries, and manual GraphQL resolvers. 
            View models combine tables, relationships, aggregations, and computed expressions with strict read-only enforcement.
          </p>
        </div>

        {/* Strategy Switcher */}
        <div className="flex justify-center mb-8">
          <div className="inline-flex p-1 rounded-lg bg-[#161B22] border border-[#30363D] font-mono text-xs">
            <button
              onClick={() => setActiveStrategy('virtual')}
              className={`px-4 py-2 rounded-md font-medium transition-colors cursor-pointer ${
                activeStrategy === 'virtual'
                  ? 'bg-[#21262D] text-amber-300 border border-amber-500/40 shadow-sm'
                  : 'text-[#8B949E] hover:text-[#F0F6FC]'
              }`}
            >
              Strategy B: Virtual Views (GraphORM-Defined)
            </button>
            <button
              onClick={() => setActiveStrategy('physical')}
              className={`px-4 py-2 rounded-md font-medium transition-colors cursor-pointer ${
                activeStrategy === 'physical'
                  ? 'bg-[#21262D] text-amber-300 border border-amber-500/40 shadow-sm'
                  : 'text-[#8B949E] hover:text-[#F0F6FC]'
              }`}
            >
              Strategy A: Physical Database Views (@@view)
            </button>
          </div>
        </div>

        {/* Strategy Detail Box */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Left Column: Code Window */}
          <div className="lg:col-span-7 rounded-lg bg-[#161B22] border border-[#30363D] overflow-hidden font-mono text-xs shadow-md">
            <div className="bg-[#161B22] px-4 py-3 border-b border-[#30363D] flex items-center justify-between">
              <span className="text-[#F0F6FC] font-semibold">
                {activeStrategy === 'virtual' ? 'view StudentDashboard (Virtual View Model)' : 'view DepartmentStats (Database-Backed View)'}
              </span>
              <span className="text-[10px] text-amber-400 bg-[#21262D] px-2 py-0.5 rounded border border-[#30363D]">
                Read-Only Invariant
              </span>
            </div>

            <div className="p-4 bg-[#0A0D12] text-[#F0F6FC] overflow-x-auto leading-relaxed max-h-[360px]">
              {activeStrategy === 'virtual' ? (
                <pre className="text-slate-200">
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
              ) : (
                <pre className="text-slate-200">
{`// Maps directly to physical or materialized view in PostgreSQL
view DepartmentStats {
  id             Int    @id
  departmentName String @map("department_name")
  studentCount   Int    @map("student_count")
  courseCount    Int    @map("course_count")

  @@view
  @@map("v_department_stats")
}`}
                </pre>
              )}
            </div>

            <div className="p-3 bg-[#161B22] border-t border-[#30363D] text-[#8B949E] text-[11px] flex items-center justify-between">
              <span>Zero In-Memory Table Dumps</span>
              <span className="text-emerald-400 font-semibold">Executed 100% in PostgreSQL</span>
            </div>
          </div>

          {/* Right Column: Architectural Guarantees */}
          <div className="lg:col-span-5 space-y-4">
            <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] space-y-2 shadow-sm">
              <div className="flex items-center gap-2 text-amber-400 font-semibold text-sm">
                <ShieldAlert className="w-4 h-4" />
                <span>Strict Read-Only Enforcement</span>
              </div>
              <p className="text-[#8B949E] text-xs leading-relaxed">
                Views represent data projections and calculations. GraphORM guarantees at both compile-time and runtime that view models cannot be mutated:
              </p>
              <ul className="text-xs text-[#8B949E] space-y-1 font-mono list-disc list-inside pt-1">
                <li>No <code className="text-[#F0F6FC]">create()</code>, <code className="text-[#F0F6FC]">update()</code>, or <code className="text-[#F0F6FC]">delete()</code> methods generated</li>
                <li>Zero write mutations exposed in GraphQL</li>
                <li>JDBC layer throws <code className="text-rose-400">ReadOnlyViewException</code></li>
              </ul>
            </div>

            <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] space-y-2 shadow-sm">
              <div className="flex items-center gap-2 text-sky-400 font-semibold text-sm">
                <Cpu className="w-4 h-4" />
                <span>SQL-Level Pushdown Filtering</span>
              </div>
              <p className="text-[#8B949E] text-xs leading-relaxed">
                GraphORM never pulls entire tables into JVM memory. All filters, sorting, and pagination applied to view models are translated directly into PostgreSQL <code className="text-sky-300">WHERE</code>, <code className="text-sky-300">ORDER BY</code>, and <code className="text-sky-300">LIMIT / OFFSET</code> clauses.
              </p>
            </div>

            <div className="p-4 rounded-lg bg-[#161B22] border border-[#30363D] space-y-2 shadow-sm">
              <div className="flex items-center gap-2 text-emerald-400 font-semibold text-sm">
                <Sparkles className="w-4 h-4" />
                <span>Join Pruning Optimization</span>
              </div>
              <p className="text-[#8B949E] text-xs leading-relaxed">
                When a GraphQL query or Java client requests fields from a virtual view without touching joined columns, GraphORM’s AST planner automatically omits the unused <code className="text-emerald-300">LEFT JOIN</code> tables from the generated SQL.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
