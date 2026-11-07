import React, { useState } from 'react';
import { api } from '../../services/api';
import { Play, Copy, Check, Clock, Database, Sparkles, Terminal, ShieldAlert, Cpu } from 'lucide-react';
import { Badge } from '../common/Badge';

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

  const handleRunQuery = async () => {
    setIsRunning(true);
    setError(null);
    try {
      const res = await api.executeGraphQl(queryText);
      setDuration(res.durationMs);
      if (res.errors && res.errors.length > 0) {
        setResponseJson(JSON.stringify({ errors: res.errors, data: res.data }, null, 2));
      } else {
        setResponseJson(JSON.stringify({ data: res.data }, null, 2));
      }
    } catch (err: any) {
      setError(err?.message || 'Failed to execute GraphQL query AST.');
    } finally {
      setIsRunning(false);
    }
  };

  const copyResponse = () => {
    if (!responseJson) return;
    navigator.clipboard.writeText(responseJson);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-4">
      {/* Studio Header */}
      <div className="p-6 rounded-2xl bg-gradient-to-r from-ink-900 via-ink-850 to-ink-900 border border-ink-700/80 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-cobalt-500/10 text-cobalt-400 border border-cobalt-500/20">
              <Terminal className="w-4 h-4" />
            </div>
            <h3 className="font-display font-bold text-lg text-white tracking-wide">
              Interactive GraphQL Query Studio
            </h3>
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
              <span className="font-mono text-xs text-slate-300 font-semibold">
                Execution Result
              </span>
              {duration != null && (
                <div className="flex items-center gap-1 text-[11px] font-mono text-laurel-300 bg-laurel-500/10 border border-laurel-500/20 px-2 py-0.5 rounded-md">
                  <Clock className="w-3 h-3" />
                  <span>{duration}ms</span>
                </div>
              )}
            </div>
            {responseJson && (
              <button
                onClick={copyResponse}
                className="flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-lg bg-ink-800 hover:bg-ink-750 text-slate-300 transition-colors cursor-pointer border border-ink-700"
              >
                {copied ? <Check className="w-3 h-3 text-laurel-400" /> : <Copy className="w-3 h-3" />}
                <span>{copied ? 'Copied' : 'Copy JSON'}</span>
              </button>
            )}
          </div>

          <div className="p-4 flex-1 bg-ink-950/80 overflow-auto min-h-[380px] max-h-[500px]">
            {error ? (
              <div className="p-4 rounded-xl bg-garnet-500/10 border border-garnet-500/20 text-garnet-300 text-xs font-mono">
                {error}
              </div>
            ) : responseJson ? (
              <pre className="font-mono text-xs text-slate-200 leading-relaxed">
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
