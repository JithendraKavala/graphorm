import React, { useState } from 'react';
import { Badge } from '../common/Badge';
import { Code2, ShieldCheck, Database, Layers, Sparkles, Copy, Check } from 'lucide-react';

export const SchemaInspector: React.FC = () => {
  const [copied, setCopied] = useState(false);

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
    </div>
  );
};
