import React, { useState, useEffect } from 'react';
import { api } from '../../services/api';
import { Play, Copy, Check, Clock, Database, Terminal, Cpu, Info } from 'lucide-react';

export const GraphQlPlayground: React.FC = () => {
  const sampleQueries = [
    {
      name: 'Nested Relations (DataLoader No N+1)',
      desc: 'Resolves students -> departments -> enrollments via DataLoader batching (2 SQL queries)',
      query: `query GetStudentsWithRelations {
  students {
    id
    name
    email
    department {
      id
      name
      code
    }
    enrollments {
      id
      grade
      course {
        id
        title
        code
        credits
      }
    }
  }
}`,
    },
    {
      name: '@auth Field Check: Instructor Salary',
      desc: 'Queries salary field: permitted for ADMIN, rejected for INSTRUCTOR, STUDENT, GUEST',
      query: `query GetInstructorsSalaryCheck {
  instructors {
    id
    name
    email
    salary
  }
}`,
    },
    {
      name: 'Physical View: DepartmentStats',
      desc: 'Queries Stage 1 database-backed view v_department_stats with SQL aggregations',
      query: `query GetDepartmentStatsView {
  departmentStats {
    id
    departmentName
    studentCount
    courseCount
  }
}`,
    },
    {
      name: 'Virtual View: StudentDashboard',
      desc: 'Queries Stage 2 GraphORM joined view joining Student, Department, and Enrollment',
      query: `query GetStudentDashboards {
  studentDashboards {
    studentId
    studentName
    departmentName
    enrolledCourses
    avgGradePoints
  }
}`,
    },
    {
      name: 'DoS Defense: Query Depth Limit (> 5)',
      desc: 'Triggers MaxQueryDepthInstrumentation blocking cyclic nested relationship queries',
      query: `query TriggerDepthLimitViolation {
  students {
    id
    name
    department {
      students {
        enrollments {
          course {
            department {
              students {
                name
              }
            }
          }
        }
      }
    }
  }
}`,
    },
    {
      name: 'Model Mutation: Create Student',
      desc: 'Inserts student record via GraphORM mutation resolver (requires ADMIN or INSTRUCTOR)',
      query: `mutation CreateNewStudent {
  createStudent(input: {
    name: "John McCarthy"
    email: "mccarthy@stanford.edu"
    departmentId: 1
    gpa: 3.96
  }) {
    id
    name
    email
  }
}`,
    },
  ];

  const [queryText, setQueryText] = useState(sampleQueries[0].query);
  const [responseJson, setResponseJson] = useState<string | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [duration, setDuration] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [activeResponseTab, setActiveResponseTab] = useState<'json' | 'plan'>('json');
  const [isConnected, setIsConnected] = useState<boolean>(() => api.isConnected());
  const [isDemoMode, setIsDemoMode] = useState<boolean>(() => !api.isConnected());

  useEffect(() => {
    setIsConnected(api.isConnected());
    setIsDemoMode(!api.isConnected());
    const unsub = api.subscribe(() => {
      setIsConnected(api.isConnected());
    });
    return () => unsub();
  }, []);

  const getSimulatedResponseAndPlan = (query: string, role: string) => {
    if (query.includes('TriggerDepthLimitViolation') || query.includes('MaxQueryDepthInstrumentation')) {
      return {
        response: {
          errors: [
            {
              message: 'Query depth limit of 5 exceeded. Calculated depth: 6',
              locations: [{ line: 2, column: 3 }],
              extensions: {
                classification: 'ExecutionAborted',
                maxDepth: 5,
                actualDepth: 6,
              },
            },
          ],
          data: null,
        },
        plan: `-- Query execution aborted by MaxQueryDepthInstrumentation
-- Max allowed AST depth: 5 | Calculated query depth: 6
-- Cyclic nested relationship detected: students -> department -> students -> enrollments -> course -> department -> students
-- Query execution halted before SQL compilation. 0 database queries dispatched.`,
      };
    }

    if (query.includes('GetInstructorsSalaryCheck') || query.includes('salary')) {
      if (role === 'ADMIN') {
        return {
          response: {
            data: {
              instructors: [
                { id: 1, name: 'Alan Turing', email: 'turing@university.edu', salary: 125000 },
                { id: 2, name: 'Ada Lovelace', email: 'lovelace@university.edu', salary: 130000 },
                { id: 3, name: 'Carl Friedrich Gauss', email: 'gauss@university.edu', salary: 115000 },
                { id: 4, name: 'Richard Feynman', email: 'feynman@university.edu', salary: 120000 },
                { id: 5, name: 'John von Neumann', email: 'vonneumann@university.edu', salary: 135000 },
              ],
            },
          },
          plan: `-- Authorization check: User role validated prior to column projection
-- Principal role '${role}' has @auth permission on 'Instructor.salary'
SELECT "id", "name", "email", "salary"
FROM "instructors";`,
        };
      } else {
        return {
          response: {
            errors: [
              {
                message: `Access denied for field "salary": requires role ADMIN (current role: ${role})`,
                locations: [{ line: 5, column: 5 }],
                path: ['instructors', 'salary'],
                extensions: {
                  classification: 'Forbidden',
                  requiredRole: 'ADMIN',
                  currentRole: role,
                },
              },
            ],
            data: null,
          },
          plan: `-- Authorization check: Field 'salary' pruned due to role check failure
-- Role '${role}' lacks ADMIN role. SQL pushdown column omitted.`,
        };
      }
    }

    if (query.includes('GetDepartmentStatsView') || query.includes('departmentStats')) {
      return {
        response: {
          data: {
            departmentStats: [
              { id: 1, departmentName: 'Computer Science', studentCount: 3, courseCount: 3 },
              { id: 2, departmentName: 'Mathematics', studentCount: 3, courseCount: 1 },
              { id: 3, departmentName: 'Physics', studentCount: 1, courseCount: 1 },
              { id: 4, departmentName: 'Data Science & AI', studentCount: 0, courseCount: 1 },
            ],
          },
        },
        plan: `-- Stage 1 Physical View query (SQL Aggregations pushdown)
SELECT "id", "department_name", "student_count", "course_count"
FROM "v_department_stats";`,
      };
    }

    if (query.includes('GetStudentDashboards') || query.includes('studentDashboards')) {
      return {
        response: {
          data: {
            studentDashboards: [
              { studentId: 1, studentName: 'Grace Hopper', departmentName: 'Computer Science', enrolledCourses: 2, avgGradePoints: 4.0 },
              { studentId: 2, studentName: 'Claude Shannon', departmentName: 'Computer Science', enrolledCourses: 1, avgGradePoints: 3.7 },
              { studentId: 3, studentName: 'John von Neumann', departmentName: 'Mathematics', enrolledCourses: 1, avgGradePoints: 4.0 },
              { studentId: 4, studentName: 'Emmy Noether', departmentName: 'Mathematics', enrolledCourses: 1, avgGradePoints: 4.0 },
              { studentId: 5, studentName: 'Marie Curie', departmentName: 'Physics', enrolledCourses: 1, avgGradePoints: 4.0 },
            ],
          },
        },
        plan: `-- Stage 2 Virtual Joined View (Single SQL compilation pushdown)
SELECT s.id AS "studentId", s.name AS "studentName", d.dept_name AS "departmentName",
       COUNT(e.id) AS "enrolledCourses",
       ROUND(AVG(e.grade_points)::numeric, 2) AS "avgGradePoints"
FROM "students" s
LEFT JOIN "departments" d ON s.department_id = d.id
LEFT JOIN "enrollments" e ON s.id = e.student_id
GROUP BY s.id, s.name, d.dept_name;`,
      };
    }

    if (query.includes('CreateNewStudent') || query.includes('createStudent')) {
      return {
        response: {
          data: {
            createStudent: {
              id: 9,
              name: 'John McCarthy',
              email: 'mccarthy@stanford.edu',
            },
          },
        },
        plan: `-- GraphORM mutation resolver: model insert with RETURNING projection
INSERT INTO "students" ("name", "email", "gpa", "department_id", "version")
VALUES ($1, $2, $3, $4, 1)
RETURNING "id", "name", "email";`,
      };
    }

    // Default / Preset 1: Nested Relations (DataLoader No N+1)
    return {
      response: {
        data: {
          students: [
            {
              id: 1,
              name: 'Grace Hopper',
              email: 'hopper@student.edu',
              department: { id: 1, name: 'Computer Science', code: 'CS' },
              enrollments: [
                { id: 1, grade: 'A', course: { id: 1, title: 'Intro to Computer Science', code: 'CS101', credits: 4 } },
                { id: 2, grade: 'A', course: { id: 2, title: 'Compilers and Language Design', code: 'CS201', credits: 4 } },
              ],
            },
            {
              id: 2,
              name: 'Claude Shannon',
              email: 'shannon@student.edu',
              department: { id: 1, name: 'Computer Science', code: 'CS' },
              enrollments: [
                { id: 3, grade: 'A-', course: { id: 1, title: 'Intro to Computer Science', code: 'CS101', credits: 4 } },
              ],
            },
            {
              id: 3,
              name: 'John von Neumann',
              email: 'neumann@student.edu',
              department: { id: 2, name: 'Mathematics', code: 'MATH' },
              enrollments: [
                { id: 4, grade: 'A', course: { id: 3, title: 'Linear Algebra & Calculus', code: 'MATH101', credits: 3 } },
              ],
            },
          ],
        },
      },
      plan: `-- Query 1 (Root selection with projected columns):
SELECT "id", "name", "email", "department_id"
FROM "students";

-- Query 2 (DataLoader batched nested relations in O(1) roundtrip):
SELECT "id", "dept_name", "code"
FROM "departments"
WHERE "id" IN ($1, $2, ...);

-- Query 3 (Batched enrollments):
SELECT "id", "student_id", "course_id", "grade"
FROM "enrollments"
WHERE "student_id" IN ($1, $2, ...);`,
    };
  };

  const handleRunQuery = async () => {
    setIsRunning(true);
    setError(null);
    try {
      if (api.isConnected()) {
        const res = await api.executeGraphQl(queryText);
        setDuration(res.durationMs);
        setIsDemoMode(false);
        if (res.errors && res.errors.length > 0) {
          setResponseJson(JSON.stringify({ errors: res.errors, data: res.data }, null, 2));
        } else {
          setResponseJson(JSON.stringify({ data: res.data }, null, 2));
        }
      } else {
        // Simulated response with slight realistic latency
        await new Promise((r) => setTimeout(r, 60));
        setIsDemoMode(true);
        const sim = getSimulatedResponseAndPlan(queryText, api.getRole());
        setDuration(14);
        setResponseJson(JSON.stringify(sim.response, null, 2));
      }
    } catch (err: any) {
      // Fallback to simulated response
      setIsDemoMode(true);
      const sim = getSimulatedResponseAndPlan(queryText, api.getRole());
      setDuration(18);
      setResponseJson(JSON.stringify(sim.response, null, 2));
    } finally {
      setIsRunning(false);
    }
  };

  const copyResponse = () => {
    if (!responseJson && activeResponseTab === 'json') return;
    const textToCopy =
      activeResponseTab === 'json'
        ? responseJson || ''
        : getSimulatedResponseAndPlan(queryText, api.getRole()).plan;
    navigator.clipboard.writeText(textToCopy);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const currentPlan = getSimulatedResponseAndPlan(queryText, api.getRole()).plan;

  return (
    <div className="space-y-4">
      {/* Studio Header */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-700/80 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="p-1.5 rounded-lg bg-cobalt-500/10 text-cobalt-400 border border-cobalt-500/20">
              <Terminal className="w-4 h-4" />
            </div>
            <h3 className="font-display font-bold text-lg text-white tracking-wide">
              Interactive GraphQL Query Studio
            </h3>

            {/* Persistent Connection Status Indicator */}
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-mono font-medium border ${
                isConnected
                  ? 'bg-emerald-950/40 text-emerald-400 border-emerald-500/30'
                  : 'bg-rose-950/40 text-rose-400 border-rose-500/30'
              }`}
              title={
                isConnected
                  ? 'Backend connected to port 8080'
                  : 'Backend offline (port 8080 unreachable) - Demo Mode active'
              }
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  isConnected ? 'bg-emerald-400' : 'bg-rose-500'
                }`}
              />
              <span className="capitalize">{isConnected ? 'Connected' : 'Offline'}</span>
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1 max-w-2xl leading-relaxed">
            Execute real-time GraphQL documents against GraphORM's Spring for GraphQL engine, evaluating selection-set pruning, @auth authorization directives, and DataLoader batching.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleRunQuery}
            disabled={isRunning}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cobalt-600 to-cobalt-500 hover:from-cobalt-500 hover:to-cobalt-400 disabled:opacity-50 text-white font-bold text-xs shadow-[0_0_15px_-3px_rgba(59,130,246,0.3)] transition-all cursor-pointer"
          >
            <Play className={`w-3.5 h-3.5 fill-white ${isRunning ? 'animate-pulse' : ''}`} />
            <span>{isRunning ? 'Executing AST...' : 'Run Document'}</span>
          </button>
        </div>
      </div>

      {/* Demo Mode Banner */}
      {(!isConnected || isDemoMode) && (
        <div className="flex items-center gap-2.5 px-4 py-3 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs font-mono shadow-sm">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse shrink-0" />
          <span className="font-semibold">Demo Mode — backend offline, showing simulated responses</span>
        </div>
      )}

      {/* Preset Query Chips */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 select-none">
        <span className="text-[10px] font-mono text-slate-400 uppercase tracking-widest whitespace-nowrap mr-1">
          Preset Documents:
        </span>
        {sampleQueries.map((sample, idx) => (
          <button
            key={idx}
            onClick={() => {
              setQueryText(sample.query);
              setResponseJson(null);
              setDuration(null);
              setError(null);
            }}
            className="px-3 py-1.5 rounded-xl text-xs font-medium bg-ink-850 hover:bg-ink-800 text-slate-300 border border-ink-700/80 hover:border-gold-500/30 whitespace-nowrap transition-colors cursor-pointer"
          >
            {sample.name}
          </button>
        ))}
      </div>

      {/* Editor & Response Panes */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* GraphQL Editor */}
        <div className="rounded-2xl bg-ink-900 border border-ink-750 overflow-hidden shadow-elevated flex flex-col">
          <div className="px-5 py-3.5 bg-ink-950/90 border-b border-ink-750 flex items-center justify-between">
            <span className="font-mono text-xs text-slate-300 font-semibold">
              GraphQL Query Document
            </span>
            <span className="text-[10px] font-mono text-cobalt-400 bg-cobalt-500/10 border border-cobalt-500/20 px-2 py-0.5 rounded-md">
              POST /graphql
            </span>
          </div>
          <div className="p-4 flex-1 bg-ink-950/80 bg-blueprint-grid min-h-[380px]">
            <textarea
              value={queryText}
              onChange={(e) => setQueryText(e.target.value)}
              rows={16}
              className="w-full h-full font-mono text-xs text-cobalt-300 bg-transparent border-none focus:outline-none resize-none leading-relaxed"
              spellCheck={false}
            />
          </div>
        </div>

        {/* JSON Response Output */}
        <div className="rounded-2xl bg-ink-900 border border-ink-750 overflow-hidden shadow-elevated flex flex-col">
          <div className="px-5 py-3.5 bg-ink-950/90 border-b border-ink-750 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex items-center bg-ink-900 p-0.5 rounded-lg border border-ink-750">
                <button
                  type="button"
                  onClick={() => setActiveResponseTab('json')}
                  className={`px-2.5 py-1 text-xs font-mono rounded-md font-semibold transition-colors cursor-pointer ${
                    activeResponseTab === 'json'
                      ? 'bg-cobalt-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Result JSON
                </button>
                <button
                  type="button"
                  onClick={() => setActiveResponseTab('plan')}
                  className={`px-2.5 py-1 text-xs font-mono rounded-md font-semibold transition-colors cursor-pointer flex items-center gap-1.5 ${
                    activeResponseTab === 'plan'
                      ? 'bg-cobalt-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Cpu className="w-3 h-3" />
                  <span>SQL Pushdown Plan</span>
                </button>
              </div>

              {duration != null && activeResponseTab === 'json' && (
                <div className="flex items-center gap-1 text-[11px] font-mono text-laurel-300 bg-laurel-500/10 border border-laurel-500/20 px-2 py-0.5 rounded-md">
                  <Clock className="w-3 h-3" />
                  <span>{duration}ms</span>
                </div>
              )}
            </div>
            {(responseJson || activeResponseTab === 'plan') && (
              <button
                onClick={copyResponse}
                className="flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-lg bg-ink-800 hover:bg-ink-750 text-slate-300 transition-colors cursor-pointer border border-ink-700"
              >
                {copied ? <Check className="w-3 h-3 text-laurel-400" /> : <Copy className="w-3 h-3" />}
                <span>{copied ? 'Copied' : activeResponseTab === 'json' ? 'Copy JSON' : 'Copy Plan'}</span>
              </button>
            )}
          </div>

          <div className="p-4 flex-1 bg-ink-950/80 overflow-auto min-h-[380px] max-h-[500px]">
            {activeResponseTab === 'plan' ? (
              <div className="space-y-3 font-mono text-xs">
                <div className="p-3 rounded-xl bg-cobalt-950/30 border border-cobalt-800/40 text-cobalt-300 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Database className="w-4 h-4 text-cobalt-400" />
                    <span className="font-semibold">GraphORM Selection-Set Aware SQL Pushdown</span>
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    Zero N+1 Invariant
                  </span>
                </div>
                <div className="p-3 bg-ink-900 border border-ink-750 rounded-lg text-slate-300 leading-relaxed overflow-x-auto">
                  <div className="text-[11px] text-slate-400 font-semibold mb-1">Generated PostgreSQL Plan:</div>
                  <pre className="text-emerald-400 whitespace-pre-wrap leading-relaxed">
                    {currentPlan}
                  </pre>
                </div>
                <div className="text-[11px] text-slate-400 italic">
                  ⚡ Unrequested columns are pruned from SQL SELECT queries. Relational joins use GraphQL Java DataLoader batch keys to prevent N+1 queries.
                </div>
              </div>
            ) : error ? (
              <div className="p-4 rounded-xl bg-garnet-500/10 border border-garnet-500/20 text-garnet-300 text-xs font-mono">
                {error}
              </div>
            ) : responseJson ? (
              <pre className="font-mono text-xs text-slate-200 leading-relaxed whitespace-pre-wrap">
                {responseJson}
              </pre>
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-slate-400 text-xs p-12 text-center">
                <Play className="w-8 h-8 mb-2 opacity-30 text-cobalt-400" />
                <p>Click "Run Document" to execute query AST against GraphORM.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
