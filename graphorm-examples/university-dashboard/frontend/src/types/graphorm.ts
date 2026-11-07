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
