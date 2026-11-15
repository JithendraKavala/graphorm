import React, { useState } from 'react';
import { Badge } from '../common/Badge';
import {
  Code2,
  ShieldCheck,
  Database,
  Layers,
  Sparkles,
  Copy,
  Check,
  ChevronDown,
  ChevronRight,
  GitBranch,
  Boxes,
  Key,
  FolderOpen,
  Folder,
  Tag,
  Lock,
} from 'lucide-react';

interface AstField {
  name: string;
  type: string;
  isId?: boolean;
  isUnique?: boolean;
  isRelation?: boolean;
  directives?: string[];
}

interface AstNode {
  name: string;
  category: 'model' | 'view';
  tableOrViewMap: string;
  annotations?: string[];
  fields: AstField[];
}

export const SchemaInspector: React.FC = () => {
  const [copied, setCopied] = useState(false);
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({
    Department: true,
    Instructor: true,
    Student: true,
    Course: true,
    Enrollment: true,
    DepartmentStats: true,
    StudentDashboard: true,
  });

  const rawSchema = `datasource db {
  provider = "postgresql"
  url      = "postgresql://localhost:5432/university_db"
}

model Department {
  id          Int          @id @default(autoincrement())
  name        String       @map("dept_name")
  code        String       @unique
  students    Student[]
  courses     Course[]
  instructors Instructor[]

  @@map("departments")
}

model Instructor {
  id           Int        @id @default(autoincrement())
  name         String
  email        String     @unique
  salary       Float?     @auth(roles: ["ADMIN"])
  departmentId Int        @map("department_id")
  department   Department @relation(fields: [departmentId], references: [id])
  courses      Course[]

  @@map("instructors")
}

model Student {
  id           Int          @id @default(autoincrement())
  name         String
  email        String       @unique
  gpa          Float?       @auth(roles: ["ADMIN", "INSTRUCTOR"])
  departmentId Int?         @map("department_id")
  department   Department?  @relation(fields: [departmentId], references: [id])
  enrollments  Enrollment[]

  @@map("students")
}

model Course {
  id           Int          @id @default(autoincrement())
  title        String
  code         String       @unique
  credits      Int
  departmentId Int          @map("department_id")
  department   Department   @relation(fields: [departmentId], references: [id])
  instructorId Int?         @map("instructor_id")
  instructor   Instructor?  @relation(fields: [instructorId], references: [id])
  enrollments  Enrollment[]

  @@map("courses")
}

model Enrollment {
  id             Int      @id @default(autoincrement())
  studentId      Int      @map("student_id")
  student        Student  @relation(fields: [studentId], references: [id])
  courseId       Int      @map("course_id")
  course         Course   @relation(fields: [courseId], references: [id])
  grade          String?  @auth(roles: ["ADMIN", "INSTRUCTOR"])
  gradePoints    Float?   @map("grade_points")
  enrollmentDate String   @map("enrollment_date")

  @@map("enrollments")
}

view DepartmentStats {
  id             Int    @id
  departmentName String @map("department_name")
  studentCount   Int    @map("student_count")
  courseCount    Int    @map("course_count")

  @@view
  @@map("v_department_stats")
}

view StudentDashboard {
  studentId       Int     @map("s.id")
  studentName     String  @map("s.name")
  departmentName  String? @map("d.dept_name")
  enrolledCourses Int     @aggregate(count: "e.id")
  avgGradePoints  Float?  @expression("ROUND(AVG(e.grade_points)::numeric, 2)")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
  @@groupBy(["s.id", "s.name", "d.dept_name"])
}`;

  const copyToClipboard = () => {
    navigator.clipboard.writeText(rawSchema);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const directives = [
    { name: '@id', desc: 'Declares primary key column for type-safe relational identity', scope: 'Field' },
    { name: '@map("column")', desc: 'Binds schema camelCase identifier to physical database snake_case', scope: 'Field' },
    { name: '@relation(...)', desc: 'Declares type-safe foreign key with zero-config DataLoader batching', scope: 'Field' },
    { name: '@auth(roles: [...])', desc: 'Enforces Spring Security role checks directly inside GraphQL execution AST', scope: 'Field' },
    { name: '@aggregate(...)', desc: 'Directs SQL compiler to push down COUNT / SUM / AVG to database engine', scope: 'View Field' },
    { name: '@expression(...)', desc: 'Evaluates native SQL projections in database engine during compile time', scope: 'View Field' },
    { name: '@@from / @@leftJoin', desc: 'Defines virtual join graph and source model dependencies for Stage 2 views', scope: 'View Model' },
    { name: '@@groupBy(...)', desc: 'Specifies SQL GROUP BY projection columns for aggregated view models', scope: 'View Model' },
  ];

  const astNodes: AstNode[] = [
    {
      name: 'Department',
      category: 'model',
      tableOrViewMap: 'departments',
      annotations: ['@@map("departments")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id', '@default(autoincrement())'] },
        { name: 'name', type: 'String', directives: ['@map("dept_name")'] },
        { name: 'code', type: 'String', isUnique: true, directives: ['@unique'] },
        { name: 'students', type: 'Student[]', isRelation: true, directives: ['1:N Relation'] },
        { name: 'courses', type: 'Course[]', isRelation: true, directives: ['1:N Relation'] },
        { name: 'instructors', type: 'Instructor[]', isRelation: true, directives: ['1:N Relation'] },
      ],
    },
    {
      name: 'Instructor',
      category: 'model',
      tableOrViewMap: 'instructors',
      annotations: ['@@map("instructors")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id', '@default(autoincrement())'] },
        { name: 'name', type: 'String' },
        { name: 'email', type: 'String', isUnique: true, directives: ['@unique'] },
        { name: 'salary', type: 'Float?', directives: ['@auth(roles: ["ADMIN"])'] },
        { name: 'departmentId', type: 'Int', directives: ['@map("department_id")'] },
        {
          name: 'department',
          type: 'Department',
          isRelation: true,
          directives: ['@relation(fields: [departmentId], references: [id])'],
        },
        { name: 'courses', type: 'Course[]', isRelation: true, directives: ['1:N Relation'] },
      ],
    },
    {
      name: 'Student',
      category: 'model',
      tableOrViewMap: 'students',
      annotations: ['@@map("students")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id', '@default(autoincrement())'] },
        { name: 'name', type: 'String' },
        { name: 'email', type: 'String', isUnique: true, directives: ['@unique'] },
        { name: 'gpa', type: 'Float?', directives: ['@auth(roles: ["ADMIN", "INSTRUCTOR"])'] },
        { name: 'departmentId', type: 'Int?', directives: ['@map("department_id")'] },
        {
          name: 'department',
          type: 'Department?',
          isRelation: true,
          directives: ['@relation(fields: [departmentId], references: [id])'],
        },
        { name: 'enrollments', type: 'Enrollment[]', isRelation: true, directives: ['1:N Relation'] },
      ],
    },
    {
      name: 'Course',
      category: 'model',
      tableOrViewMap: 'courses',
      annotations: ['@@map("courses")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id', '@default(autoincrement())'] },
        { name: 'title', type: 'String' },
        { name: 'code', type: 'String', isUnique: true, directives: ['@unique'] },
        { name: 'credits', type: 'Int' },
        { name: 'departmentId', type: 'Int', directives: ['@map("department_id")'] },
        {
          name: 'department',
          type: 'Department',
          isRelation: true,
          directives: ['@relation(fields: [departmentId], references: [id])'],
        },
        { name: 'instructorId', type: 'Int?', directives: ['@map("instructor_id")'] },
        {
          name: 'instructor',
          type: 'Instructor?',
          isRelation: true,
          directives: ['@relation(fields: [instructorId], references: [id])'],
        },
        { name: 'enrollments', type: 'Enrollment[]', isRelation: true, directives: ['1:N Relation'] },
      ],
    },
    {
      name: 'Enrollment',
      category: 'model',
      tableOrViewMap: 'enrollments',
      annotations: ['@@map("enrollments")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id', '@default(autoincrement())'] },
        { name: 'studentId', type: 'Int', directives: ['@map("student_id")'] },
        {
          name: 'student',
          type: 'Student',
          isRelation: true,
          directives: ['@relation(fields: [studentId], references: [id])'],
        },
        { name: 'courseId', type: 'Int', directives: ['@map("course_id")'] },
        {
          name: 'course',
          type: 'Course',
          isRelation: true,
          directives: ['@relation(fields: [courseId], references: [id])'],
        },
        { name: 'grade', type: 'String?', directives: ['@auth(roles: ["ADMIN", "INSTRUCTOR"])'] },
        { name: 'gradePoints', type: 'Float?', directives: ['@map("grade_points")'] },
        { name: 'enrollmentDate', type: 'String', directives: ['@map("enrollment_date")'] },
      ],
    },
    {
      name: 'DepartmentStats',
      category: 'view',
      tableOrViewMap: 'v_department_stats',
      annotations: ['@@view (Physical View)', '@@map("v_department_stats")'],
      fields: [
        { name: 'id', type: 'Int', isId: true, directives: ['@id'] },
        { name: 'departmentName', type: 'String', directives: ['@map("department_name")'] },
        { name: 'studentCount', type: 'Int', directives: ['@map("student_count")', 'SQL COUNT(*)'] },
        { name: 'courseCount', type: 'Int', directives: ['@map("course_count")', 'SQL COUNT(*)'] },
      ],
    },
    {
      name: 'StudentDashboard',
      category: 'view',
      tableOrViewMap: 'Student + Department + Enrollment',
      annotations: [
        '@@view (Virtual Joined View)',
        '@@from(Student, as: "s")',
        '@@leftJoin(Department, as: "d", on: "s.departmentId == d.id")',
        '@@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")',
        '@@groupBy(["s.id", "s.name", "d.dept_name"])',
      ],
      fields: [
        { name: 'studentId', type: 'Int', directives: ['@map("s.id")'] },
        { name: 'studentName', type: 'String', directives: ['@map("s.name")'] },
        { name: 'departmentName', type: 'String?', directives: ['@map("d.dept_name")'] },
        { name: 'enrolledCourses', type: 'Int', directives: ['@aggregate(count: "e.id")'] },
        {
          name: 'avgGradePoints',
          type: 'Float?',
          directives: ['@expression("ROUND(AVG(e.grade_points)::numeric, 2)")'],
        },
      ],
    },
  ];

  const toggleNode = (name: string) => {
    setExpandedNodes((prev) => ({
      ...prev,
      [name]: !prev[name],
    }));
  };

  const expandAll = () => {
    const allTrue: Record<string, boolean> = {};
    astNodes.forEach((node) => {
      allTrue[node.name] = true;
    });
    setExpandedNodes(allTrue);
  };

  const collapseAll = () => {
    const allFalse: Record<string, boolean> = {};
    astNodes.forEach((node) => {
      allFalse[node.name] = false;
    });
    setExpandedNodes(allFalse);
  };

  return (
    <div className="space-y-4">
      {/* Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm">
          <div className="flex items-center gap-2.5 text-gold-400 mb-2">
            <Code2 className="w-5 h-5" />
            <h4 className="font-display font-bold text-sm text-white">Declarative Schema DSL</h4>
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            Parsed into an immutable AST metadata graph by <code className="text-gold-300 font-mono">graphorm-schema</code> with strict compile-time diagnostics.
          </p>
        </div>

        <div className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm">
          <div className="flex items-center gap-2.5 text-cobalt-400 mb-2">
            <ShieldCheck className="w-5 h-5" />
            <h4 className="font-display font-bold text-sm text-white">Automatic DataLoader Batching</h4>
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            Eliminates N+1 query loops automatically across 1:1, 1:N, and N:M relationships with selection-set projection pruning.
          </p>
        </div>

        <div className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm">
          <div className="flex items-center gap-2.5 text-laurel-400 mb-2">
            <Sparkles className="w-5 h-5" />
            <h4 className="font-display font-bold text-sm text-white">Strict Read-Only Invariants</h4>
          </div>
          <p className="text-xs text-slate-400 leading-relaxed">
            View models cannot be mutated by contract. Mutation inputs are excluded at code generation and schema wiring.
          </p>
        </div>
      </div>

      {/* Code Viewer & Directives Table */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Schema Code Panel */}
        <div className="lg:col-span-2 rounded-2xl bg-ink-900 border border-ink-750 overflow-hidden shadow-elevated flex flex-col">
          <div className="px-5 py-3.5 bg-ink-950/90 border-b border-ink-750 flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="w-2.5 h-2.5 rounded-full bg-garnet-500/80" />
              <span className="w-2.5 h-2.5 rounded-full bg-gold-500/80" />
              <span className="w-2.5 h-2.5 rounded-full bg-laurel-500/80" />
              <span className="ml-2 font-mono text-xs text-slate-400">schema.graphorm</span>
            </div>
            <button
              onClick={copyToClipboard}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-xl bg-ink-800 hover:bg-ink-750 text-slate-200 font-mono transition-colors cursor-pointer border border-ink-700"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-laurel-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy DSL'}</span>
            </button>
          </div>

          <div className="p-5 font-mono text-xs overflow-x-auto max-h-[580px] bg-ink-950/90 text-slate-300 leading-relaxed bg-blueprint-grid">
            <pre className="text-slate-300 font-mono">
              {rawSchema}
            </pre>
          </div>
        </div>

        {/* Directives Table */}
        <div className="space-y-4">
          <div className="p-5 rounded-2xl bg-ink-850/90 border border-ink-700/70 shadow-sm">
            <h4 className="font-display font-bold text-sm text-white mb-3">
              GraphORM DSL Directives
            </h4>
            <div className="space-y-2.5">
              {directives.map((dir, idx) => (
                <div key={idx} className="p-3 rounded-xl bg-ink-900/80 border border-ink-750">
                  <div className="flex items-center justify-between">
                    <code className="text-xs font-mono font-semibold text-gold-300">
                      {dir.name}
                    </code>
                    <Badge variant="slate" size="sm">
                      {dir.scope}
                    </Badge>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{dir.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Collapsible AST Tree Panel */}
      <div className="rounded-2xl bg-ink-900 border border-ink-750 overflow-hidden shadow-elevated">
        {/* AST Header */}
        <div className="px-5 py-4 bg-ink-950/95 border-b border-ink-750 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-accent/10 text-accent border border-accent/20">
              <GitBranch className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-display font-bold text-sm text-white tracking-wide">
                  Parsed Schema AST Tree Hierarchy
                </h3>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-accent/10 text-accent border border-accent/30">
                  {astNodes.length} Root Nodes
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                Compiler AST representation generated by GraphORM Schema Engine. Expand nodes to inspect field types, relational edges, and directives.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={expandAll}
              className="px-2.5 py-1 text-xs rounded-lg bg-ink-800 hover:bg-ink-750 text-slate-300 font-mono transition-colors border border-ink-700 cursor-pointer"
            >
              Expand All
            </button>
            <button
              onClick={collapseAll}
              className="px-2.5 py-1 text-xs rounded-lg bg-ink-800 hover:bg-ink-750 text-slate-300 font-mono transition-colors border border-ink-700 cursor-pointer"
            >
              Collapse All
            </button>
          </div>
        </div>

        {/* Tree Content */}
        <div className="p-5 font-mono text-xs space-y-3 bg-ink-950/70">
          {astNodes.map((node) => {
            const isExpanded = !!expandedNodes[node.name];
            const isModel = node.category === 'model';
            return (
              <div
                key={node.name}
                className="rounded-xl border border-ink-750 bg-ink-900/90 overflow-hidden transition-all shadow-sm"
              >
                {/* Node Row Header */}
                <button
                  type="button"
                  onClick={() => toggleNode(node.name)}
                  className="w-full px-4 py-3 bg-ink-850/80 hover:bg-ink-800/90 flex items-center justify-between text-left transition-colors cursor-pointer select-none"
                >
                  <div className="flex items-center gap-2.5">
                    {isExpanded ? (
                      <ChevronDown className="w-4 h-4 text-accent shrink-0" />
                    ) : (
                      <ChevronRight className="w-4 h-4 text-slate-500 shrink-0" />
                    )}

                    {isExpanded ? (
                      <FolderOpen className={`w-4 h-4 ${isModel ? 'text-accent' : 'text-purple-400'}`} />
                    ) : (
                      <Folder className={`w-4 h-4 ${isModel ? 'text-accent' : 'text-purple-400'}`} />
                    )}

                    <span className="font-bold text-white tracking-wide text-xs">
                      {node.name}
                    </span>

                    <span
                      className={`text-[10px] uppercase font-mono px-2 py-0.5 rounded font-semibold ${
                        isModel
                          ? 'bg-accent/10 text-accent border border-accent/20'
                          : 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                      }`}
                    >
                      {node.category}
                    </span>
                  </div>

                  <div className="flex items-center gap-2">
                    {node.annotations?.map((ann, i) => (
                      <span
                        key={i}
                        className="hidden sm:inline-block text-[10px] font-mono px-2 py-0.5 rounded bg-ink-950 text-slate-400 border border-ink-700/60"
                      >
                        {ann}
                      </span>
                    ))}
                    <span className="text-[11px] text-slate-500 font-mono">
                      {node.fields.length} fields
                    </span>
                  </div>
                </button>

                {/* Collapsible Fields Tree */}
                {isExpanded && (
                  <div className="p-4 border-t border-ink-800 space-y-1.5 bg-canvas/60">
                    {node.fields.map((field, idx) => {
                      const isLast = idx === node.fields.length - 1;
                      return (
                        <div
                          key={field.name}
                          className="flex flex-col sm:flex-row sm:items-center justify-between py-1.5 px-3 rounded-lg hover:bg-ink-800/40 transition-colors gap-2"
                        >
                          <div className="flex items-center gap-2">
                            <span className="text-slate-600 select-none text-xs">
                              {isLast ? '└──' : '├──'}
                            </span>
                            <span className="text-slate-200 font-semibold">{field.name}:</span>
                            <span
                              className={`text-[11px] font-mono ${
                                field.isRelation
                                  ? 'text-amber-300 font-bold'
                                  : field.type.includes('Int') || field.type.includes('Float')
                                  ? 'text-emerald-400'
                                  : 'text-accent'
                              }`}
                            >
                              {field.type}
                            </span>
                          </div>

                          {field.directives && field.directives.length > 0 && (
                            <div className="flex items-center gap-1.5 flex-wrap pl-6 sm:pl-0">
                              {field.directives.map((dir, dIdx) => {
                                const isAuth = dir.startsWith('@auth');
                                const isId = dir === '@id';
                                const isUniq = dir === '@unique';
                                return (
                                  <span
                                    key={dIdx}
                                    className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                                      isAuth
                                        ? 'bg-rose-950/40 text-rose-300 border-rose-500/30'
                                        : isId
                                        ? 'bg-amber-950/40 text-amber-300 border-amber-500/30 font-semibold'
                                        : isUniq
                                        ? 'bg-emerald-950/40 text-emerald-300 border-emerald-500/30'
                                        : 'bg-ink-850 text-slate-400 border-ink-700'
                                    }`}
                                  >
                                    {dir}
                                  </span>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
