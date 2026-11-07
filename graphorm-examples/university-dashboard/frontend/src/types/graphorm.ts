/**
 * GraphORM Schema Type Definitions for University Domain
 */

export interface Department {
  id: number;
  name: string;
  code: string;
  students?: Student[];
  courses?: Course[];
  instructors?: Instructor[];
}

export interface Instructor {
  id: number;
  name: string;
  email: string;
  salary?: number | null;
  departmentId: number;
  department?: Department | null;
  courses?: Course[];
}

export interface Student {
  id: number;
  name: string;
  email: string;
  gpa?: number | null;
  departmentId?: number | null;
  department?: Department | null;
  enrollments?: Enrollment[];
}

export interface Course {
  id: number;
  title: string;
  code: string;
  credits: number;
  departmentId: number;
  department?: Department | null;
  instructorId?: number | null;
  instructor?: Instructor | null;
  enrollments?: Enrollment[];
}

export interface Enrollment {
  id: number;
  studentId: number;
  student?: Student | null;
  courseId: number;
  course?: Course | null;
  grade?: string | null;
  gradePoints?: number | null;
  enrollmentDate: string;
}

// GraphORM View Models
export interface DepartmentStats {
  id: number;
  departmentName: string;
  studentCount: number;
  courseCount: number;
}

export interface StudentDashboard {
  studentId: number;
  studentName: string;
  departmentName: string | null;
  enrolledCourses: number;
  avgGradePoints: number | null;
}

// Create/Update Inputs
export interface CreateStudentInput {
  name: string;
  email: string;
  gpa?: number;
  departmentId?: number;
}

export interface UpdateStudentInput {
  name?: string;
  email?: string;
  gpa?: number;
  departmentId?: number;
}

export interface CreateDepartmentInput {
  name: string;
  code: string;
}

export interface UpdateDepartmentInput {
  name?: string;
  code?: string;
}

export interface CreateCourseInput {
  title: string;
  code: string;
  credits: number;
  departmentId: number;
  instructorId?: number;
}

export interface UpdateCourseInput {
  title?: string;
  code?: string;
  credits?: number;
  departmentId?: number;
  instructorId?: number;
}

export interface CreateEnrollmentInput {
  studentId: number;
  courseId: number;
  grade?: string;
  gradePoints?: number;
  enrollmentDate: string;
}

export interface UpdateEnrollmentInput {
  studentId?: number;
  courseId?: number;
  grade?: string;
  gradePoints?: number;
  enrollmentDate?: string;
}

// Navigation & View State
export type ActiveTab =
  | 'landing'
  | 'dashboard'
  | 'students'
  | 'departments'
  | 'courses'
  | 'enrollments'
  | 'analytics'
  | 'schema-explorer'
  | 'graphql-playground';

export type DataSourceMode = 'graphql' | 'rest';

export interface DashboardWidgetConfig {
  id: string;
  title: string;
  visible: boolean;
  order: number;
}

export interface ActivityItem {
  id: string;
  timestamp: string;
  type: 'enrollment' | 'grade' | 'student_created' | 'course_added';
  title: string;
  description: string;
  badge: string;
}

// ==========================================
// AUTHENTICATION & ROLE-BASED ACCESS CONTROL
// ==========================================

export type AcademicRole = 'ADMIN' | 'INSTRUCTOR' | 'STUDENT' | 'GUEST';

export interface UserRoleProfile {
  id: AcademicRole;
  label: string;
  badgeColor: string;
  username: string;
  authHeaderValue: string;
  description: string;
  badgeText: string;
  permissions: {
    canViewSalary: boolean;
    canViewGpa: boolean;
    canViewGrades: boolean;
    canMutate: boolean;
  };
}

export const ROLE_PROFILES: Record<AcademicRole, UserRoleProfile> = {
  ADMIN: {
    id: 'ADMIN',
    label: 'Dean / Admin',
    badgeColor: 'border-emerald-500/40 text-emerald-400 bg-emerald-950/30',
    username: 'admin',
    authHeaderValue: 'admin:admin123',
    badgeText: 'ADMIN',
    description: 'Full administrative access: all models, mutations, and @auth protected fields (Salary, GPA, Grades)',
    permissions: {
      canViewSalary: true,
      canViewGpa: true,
      canViewGrades: true,
      canMutate: true,
    },
  },
  INSTRUCTOR: {
    id: 'INSTRUCTOR',
    label: 'Faculty / Instructor',
    badgeColor: 'border-sky-500/40 text-sky-400 bg-sky-950/30',
    username: 'instructor',
    authHeaderValue: 'instructor:prof123',
    badgeText: 'FACULTY',
    description: 'Faculty access: GPA & Grade visibility, course/grade mutations. Restricted from Instructor Salary',
    permissions: {
      canViewSalary: false,
      canViewGpa: true,
      canViewGrades: true,
      canMutate: true,
    },
  },
  STUDENT: {
    id: 'STUDENT',
    label: 'Scholar / Student',
    badgeColor: 'border-purple-500/40 text-purple-400 bg-purple-950/30',
    username: 'student',
    authHeaderValue: 'student:student123',
    badgeText: 'SCHOLAR',
    description: 'Student access: public catalog only. Restricted from peer GPA, letter grades, faculty salary, and mutations',
    permissions: {
      canViewSalary: false,
      canViewGpa: false,
      canViewGrades: false,
      canMutate: false,
    },
  },
  GUEST: {
    id: 'GUEST',
    label: 'Guest / Public',
    badgeColor: 'border-slate-500/40 text-slate-400 bg-slate-900/40',
    username: 'anonymous',
    authHeaderValue: '',
    badgeText: 'PUBLIC',
    description: 'Unauthenticated public visitor: read-only public catalog. All @auth fields and mutations are blocked',
    permissions: {
      canViewSalary: false,
      canViewGpa: false,
      canViewGrades: false,
      canMutate: false,
    },
  },
};

// ==========================================
// PAGINATION & SORTING GENERIC TYPES
// ==========================================

export interface PaginationParams {
  page: number;
  pageSize: number;
}

export interface PaginationState {
  currentPage: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface PaginatedResult<T> {
  items: T[];
  pagination: PaginationState;
  departments?: Department[];
  instructors?: Instructor[];
  courses?: Course[];
  students?: Student[];
  departmentStats?: DepartmentStats[];
}

export interface QueryOptions {
  page?: number;
  pageSize?: number;
  search?: string;
  departmentId?: string | number;
  instructorId?: string | number;
  credits?: string | number;
  courseId?: string | number;
  grade?: string;
  minGpa?: number;
  minCourses?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export type SortDirection = 'asc' | 'desc';

export interface SortState<T> {
  field: keyof T;
  order: SortDirection;
}

// ==========================================
// GRAPHQL EXECUTION & TELEMETRY
// ==========================================

export interface GraphQLErrorLocation {
  line: number;
  column: number;
}

export interface GraphQLErrorItem {
  message: string;
  locations?: GraphQLErrorLocation[];
  path?: (string | number)[];
  extensions?: {
    classification?: string;
    code?: string;
    exception?: string;
    [key: string]: unknown;
  };
}

export interface GraphQLResponse<T = unknown> {
  data?: T;
  errors?: GraphQLErrorItem[];
}
