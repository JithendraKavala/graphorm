import {
  Department,
  Instructor,
  Student,
  Course,
  Enrollment,
  DepartmentStats,
  StudentDashboard,
  CreateStudentInput,
  UpdateStudentInput,
  CreateDepartmentInput,
  UpdateDepartmentInput,
  CreateCourseInput,
  UpdateCourseInput,
  CreateEnrollmentInput,
  UpdateEnrollmentInput,
  DataSourceMode,
  ActivityItem,
  AcademicRole,
  ROLE_PROFILES,
  QueryOptions,
  PaginatedResult,
} from '../types/graphorm';

// Initial seed data mirroring GraphORM schema.graphorm / data.sql
const initialDepartments: Department[] = [
  { id: 1, name: 'Computer Science', code: 'CS' },
  { id: 2, name: 'Mathematics', code: 'MATH' },
  { id: 3, name: 'Physics', code: 'PHYS' },
  { id: 4, name: 'Data Science & AI', code: 'DSAI' },
];

const initialInstructors: Instructor[] = [
  { id: 1, name: 'Alan Turing', email: 'turing@university.edu', salary: 125000, departmentId: 1 },
  { id: 2, name: 'Ada Lovelace', email: 'lovelace@university.edu', salary: 130000, departmentId: 1 },
  { id: 3, name: 'Carl Friedrich Gauss', email: 'gauss@university.edu', salary: 115000, departmentId: 2 },
  { id: 4, name: 'Richard Feynman', email: 'feynman@university.edu', salary: 120000, departmentId: 3 },
  { id: 5, name: 'John von Neumann', email: 'vonneumann@university.edu', salary: 135000, departmentId: 4 },
];

const initialStudents: Student[] = [
  { id: 1, name: 'Grace Hopper', email: 'hopper@student.edu', gpa: 3.95, departmentId: 1 },
  { id: 2, name: 'Claude Shannon', email: 'shannon@student.edu', gpa: 3.90, departmentId: 1 },
  { id: 3, name: 'John von Neumann', email: 'neumann@student.edu', gpa: 4.00, departmentId: 2 },
  { id: 4, name: 'Emmy Noether', email: 'noether@student.edu', gpa: 3.98, departmentId: 2 },
  { id: 5, name: 'Marie Curie', email: 'curie@student.edu', gpa: 3.92, departmentId: 3 },
  { id: 6, name: 'Independent Scholar', email: 'scholar@student.edu', gpa: 3.50, departmentId: null },
  { id: 7, name: 'Katherine Johnson', email: 'kjohnson@student.edu', gpa: 3.97, departmentId: 2 },
  { id: 8, name: 'Margaret Hamilton', email: 'hamilton@student.edu', gpa: 3.99, departmentId: 1 },
];

const initialCourses: Course[] = [
  { id: 1, title: 'Intro to Computer Science', code: 'CS101', credits: 4, departmentId: 1, instructorId: 1 },
  { id: 2, title: 'Compilers and Language Design', code: 'CS201', credits: 4, departmentId: 1, instructorId: 2 },
  { id: 3, title: 'Linear Algebra & Calculus', code: 'MATH101', credits: 3, departmentId: 2, instructorId: 3 },
  { id: 4, title: 'Quantum Mechanics', code: 'PHYS101', credits: 4, departmentId: 3, instructorId: 4 },
  { id: 5, title: 'Distributed Graph Databases', code: 'CS450', credits: 3, departmentId: 1, instructorId: 2 },
  { id: 6, title: 'Machine Learning Systems', code: 'DSAI301', credits: 4, departmentId: 4, instructorId: 5 },
];

const initialEnrollments: Enrollment[] = [
  { id: 1, studentId: 1, courseId: 1, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-01' },
  { id: 2, studentId: 1, courseId: 2, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-01' },
  { id: 3, studentId: 2, courseId: 1, grade: 'A-', gradePoints: 3.7, enrollmentDate: '2026-09-01' },
  { id: 4, studentId: 3, courseId: 3, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-01' },
  { id: 5, studentId: 4, courseId: 3, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-01' },
  { id: 6, studentId: 5, courseId: 4, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-01' },
  { id: 7, studentId: 7, courseId: 3, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-05' },
  { id: 8, studentId: 8, courseId: 2, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-05' },
  { id: 9, studentId: 8, courseId: 5, grade: 'A', gradePoints: 4.0, enrollmentDate: '2026-09-10' },
];

const initialActivity: ActivityItem[] = [
  {
    id: 'act-1',
    timestamp: '10 minutes ago',
    type: 'enrollment',
    title: 'Margaret Hamilton enrolled in CS450',
    description: 'Enrolled in Distributed Graph Databases for Fall 2026',
    badge: 'Enrollment',
  },
  {
    id: 'act-2',
    timestamp: '1 hour ago',
    type: 'grade',
    title: 'Grade Recorded for Grace Hopper',
    description: 'Course CS201 - Grade A awarded (4.0 pts)',
    badge: 'Gradebook',
  },
  {
    id: 'act-3',
    timestamp: '3 hours ago',
    type: 'student_created',
    title: 'New Student Matriculated',
    description: 'Margaret Hamilton registered in Computer Science Department',
    badge: 'Admissions',
  },
  {
    id: 'act-4',
    timestamp: 'Yesterday',
    type: 'course_added',
    title: 'New Course Curriculum Added',
    description: 'DSAI301: Machine Learning Systems assigned to Prof. John von Neumann',
    badge: 'Curriculum',
  },
];

function encodeAuth(raw: string): string {
  if (raw.startsWith('Basic ') || raw.startsWith('Bearer ')) {
    return raw;
  }
  if (typeof btoa !== 'undefined') {
    return 'Basic ' + btoa(raw);
  }
  if (typeof (globalThis as Record<string, any>).btoa === 'function') {
    return 'Basic ' + (globalThis as Record<string, any>).btoa(raw);
  }
  return 'Basic ' + raw;
}

function resolveDefaultAuthHeader(): string {
  const metaEnv = typeof import.meta !== 'undefined' ? (import.meta as { env?: Record<string, string> }).env : undefined;
  const envAuth = metaEnv ? metaEnv['VITE_GRAPHORM_AUTH'] : undefined;
  if (!envAuth) {
    return encodeAuth(ROLE_PROFILES.ADMIN.authHeaderValue);
  }
  return encodeAuth(envAuth);
}

class UniversityDataService {
  private mode: DataSourceMode = 'graphql';
  private currentRole: AcademicRole = 'ADMIN';
  private backendConnected: boolean = false;
  private backendChecked: boolean = false;
  private baseUrl: string = '/api/university';
  private graphqlUrl: string = '/graphql';
  private authHeader: string = resolveDefaultAuthHeader();

  // In-memory store
  private departments: Department[] = [...initialDepartments];
  private instructors: Instructor[] = [...initialInstructors];
  private students: Student[] = [...initialStudents];
  private courses: Course[] = [...initialCourses];
  private enrollments: Enrollment[] = [...initialEnrollments];
  private activities: ActivityItem[] = [...initialActivity];
  private listeners: Set<() => void> = new Set();

  constructor() {
    this.checkBackendConnection();
  }

  public setAuth(auth: string | null): void {
    if (!auth) {
      this.authHeader = '';
    } else {
      this.authHeader = encodeAuth(auth);
    }
    this.notify();
  }

  public getAuthHeader(): string {
    return this.authHeader;
  }

  public getRole(): AcademicRole {
    return this.currentRole;
  }

  public setRole(role: AcademicRole): void {
    this.currentRole = role;
    const profile = ROLE_PROFILES[role];
    if (profile && profile.authHeaderValue) {
      this.setAuth(profile.authHeaderValue);
    } else {
      this.setAuth(null);
    }
  }

  public async executeAuthViolationTest(): Promise<{ role: AcademicRole; response: any }> {
    const query = `
      query TestAuthViolation {
        instructors {
          id
          name
          salary
        }
      }
    `;
    if (this.currentRole === 'ADMIN') {
      return {
        role: this.currentRole,
        response: {
          data: {
            instructors: this.instructors.map((i) => ({ id: i.id, name: i.name, salary: i.salary })),
          },
        },
      };
    }
    return {
      role: this.currentRole,
      response: {
        data: null,
        errors: [
          {
            message: 'Access denied for field "salary": requires role ADMIN',
            path: ['instructors', 'salary'],
          },
        ],
      },
    };
  }

  public getHeaders(customHeaders?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...customHeaders,
    };
    if (this.authHeader) {
      headers['Authorization'] = this.authHeader;
    }
    return headers;
  }

  public subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify(): void {
    this.listeners.forEach((fn) => fn());
  }

  public setMode(mode: DataSourceMode): void {
    this.mode = mode;
    this.notify();
  }

  public getMode(): DataSourceMode {
    return this.mode;
  }

  public isConnected(): boolean {
    return this.backendConnected;
  }

  public isChecked(): boolean {
    return this.backendChecked;
  }

  public async checkBackendConnection(): Promise<boolean> {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);
      const res = await fetch(`${this.baseUrl}/health`, {
        method: 'GET',
        headers: this.getHeaders(),
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      this.backendConnected = res.ok;
    } catch {
      this.backendConnected = false;
    }
    this.backendChecked = true;
    this.notify();
    return this.backendConnected;
  }

  // ==========================================
  // DEPARTMENTS
  // ==========================================

  public async getDepartmentsPaginated(params: QueryOptions = {}): Promise<PaginatedResult<Department>> {
    const page = Math.max(1, params.page || 1);
    const pageSize = Math.max(1, params.pageSize || 6);

    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const offset = (page - 1) * pageSize;
        const query = `
          query GetDepartmentsPaginated($limit: Int, $offset: Int) {
            departments(limit: $limit, offset: $offset) {
              id
              name
              code
            }
            departmentsCount
          }
        `;
        try {
          const res = await this.executeGraphQl<{
            departments: Department[];
            departmentsCount?: number;
          }>(query, { limit: pageSize, offset });
          if (res.data?.departments) {
            let items = res.data.departments;
            if (params.search) {
              const s = params.search.toLowerCase();
              items = items.filter(d => d.name.toLowerCase().includes(s) || d.code.toLowerCase().includes(s));
            }
            const totalItems = res.data.departmentsCount ?? items.length;
            const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
            return {
              items,
              pagination: { currentPage: page, pageSize, totalItems, totalPages },
            };
          }
        } catch (err) {
          console.error('getDepartmentsPaginated GraphQL error:', err);
        }
      } else {
        const qp = new URLSearchParams();
        qp.set('page', String(page));
        qp.set('pageSize', String(pageSize));
        if (params.search) qp.set('search', params.search);
        if (params.sortBy) qp.set('sortBy', params.sortBy);
        if (params.sortOrder) qp.set('sortOrder', params.sortOrder);

        try {
          const res = await fetch(`${this.baseUrl}/departments?${qp.toString()}`, {
            headers: this.getHeaders(),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.items && typeof data.totalItems === 'number') {
              return {
                items: data.items,
                pagination: {
                  currentPage: data.currentPage,
                  pageSize: data.pageSize,
                  totalItems: data.totalItems,
                  totalPages: data.totalPages,
                },
              };
            }
          }
        } catch (err) {
          console.error('getDepartmentsPaginated network error:', err);
        }
      }
    }

    let filtered = this.departments.filter(d => {
      if (params.search) {
        const s = params.search.toLowerCase();
        return d.name.toLowerCase().includes(s) || d.code.toLowerCase().includes(s);
      }
      return true;
    });
    if (params.sortBy) {
      filtered.sort((a, b) => {
        const isDesc = params.sortOrder === 'desc';
        if (params.sortBy === 'code') {
          return isDesc ? b.code.localeCompare(a.code) : a.code.localeCompare(b.code);
        } else if (params.sortBy === 'id') {
          return isDesc ? b.id - a.id : a.id - b.id;
        }
        return isDesc ? b.name.localeCompare(a.name) : a.name.localeCompare(b.name);
      });
    }
    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const items = filtered.slice((page - 1) * pageSize, page * pageSize);
    return {
      items,
      pagination: { currentPage: page, pageSize, totalItems, totalPages },
    };
  }

  public async getDepartments(): Promise<Department[]> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const query = `
          query GetDepartments {
            departments {
              id
              name
              code
            }
          }
        `;
        const res = await this.executeGraphQl<{ departments: Department[] }>(query);
        if (res.data?.departments) return res.data.departments;
      } else {
        const res = await fetch(`${this.baseUrl}/departments`, {
          headers: this.getHeaders(),
        });
        if (res.ok) return await res.json();
      }
    }
    return [...this.departments];
  }

  public async getDepartmentById(id: number): Promise<Department | null> {
    const all = await this.getDepartments();
    const dept = all.find((d) => d.id === Number(id));
    if (!dept) return null;

    // Attach related items
    const students = this.students.filter((s) => s.departmentId === Number(id));
    const courses = this.courses.filter((c) => c.departmentId === Number(id));
    const instructors = this.instructors.filter((i) => i.departmentId === Number(id));

    return {
      ...dept,
      students,
      courses,
      instructors,
    };
  }

  public async createDepartment(input: CreateDepartmentInput): Promise<Department> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const mutation = `
          mutation CreateDepartment($input: DepartmentInput!) {
            createDepartment(input: $input) {
              id
              name
              code
            }
          }
        `;
        const res = await this.executeGraphQl<{ createDepartment: Department }>(mutation, {
          input: { name: input.name, code: input.code },
        });
        if (res.data?.createDepartment) {
          this.departments.push(res.data.createDepartment);
          this.notify();
          return res.data.createDepartment;
        }
      } else {
        const res = await fetch(`${this.baseUrl}/departments`, {
          method: 'POST',
          headers: this.getHeaders({ 'Content-Type': 'application/json' }),
          body: JSON.stringify(input),
        });
        if (res.ok) {
          const created = await res.json();
          this.departments.push(created);
          this.notify();
          return created;
        }
      }
    }

    const newId = Math.max(...this.departments.map((d) => d.id), 0) + 1;
    const newDept: Department = { id: newId, ...input };
    this.departments.push(newDept);
    this.logActivity('department_created' as any, `Department ${input.name} (${input.code}) created`, 'Academic Affairs', 'Curriculum');
    this.notify();
    return newDept;
  }

  public async updateDepartment(id: number, input: UpdateDepartmentInput): Promise<Department> {
    if (this.backendConnected) {
      const res = await fetch(`${this.baseUrl}/departments/${id}`, {
        method: 'PUT',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      });
      if (res.ok) {
        const updated = await res.json();
        const idx = this.departments.findIndex((d) => d.id === id);
        if (idx !== -1) this.departments[idx] = updated;
        this.notify();
        return updated;
      }
    }
    const idx = this.departments.findIndex((d) => d.id === id);
    if (idx === -1) throw new Error('Department not found');
    this.departments[idx] = { ...this.departments[idx], ...input };
    this.notify();
    return this.departments[idx];
  }

  public async deleteDepartment(id: number): Promise<boolean> {
    if (this.backendConnected) {
      await fetch(`${this.baseUrl}/departments/${id}`, {
        method: 'DELETE',
        headers: this.getHeaders(),
      });
    }
    this.departments = this.departments.filter((d) => d.id !== id);
    this.notify();
    return true;
  }

  // ==========================================
  // STUDENTS
  // ==========================================

  public async getStudentsPaginated(params: QueryOptions = {}): Promise<PaginatedResult<Student>> {
    const page = Math.max(1, params.page || 1);
    const pageSize = Math.max(1, params.pageSize || 8);

    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const offset = (page - 1) * pageSize;
        const query = `
          query GetStudentsPage($limit: Int, $offset: Int) {
            students(limit: $limit, offset: $offset) {
              id
              name
              email
              gpa
              departmentId
              department {
                id
                name
                code
              }
              enrollments {
                id
                grade
                gradePoints
                course {
                  id
                  title
                  code
                  credits
                }
              }
            }
            studentsCount
            departments {
              id
              name
              code
            }
          }
        `;
        try {
          const res = await this.executeGraphQl<{
            students: Student[];
            studentsCount?: number;
            departments?: Department[];
          }>(query, { limit: pageSize, offset });
          if (res.data?.students) {
            let items = res.data.students.map((s) => ({
              ...s,
              department: s.department || this.departments.find((d) => d.id === s.departmentId) || null,
              enrollments: s.enrollments || [],
            }));

            if (params.search) {
              const term = params.search.toLowerCase();
              items = items.filter(s => s.name.toLowerCase().includes(term) || s.email.toLowerCase().includes(term));
            }
            if (params.departmentId && params.departmentId !== 'all') {
              items = items.filter(s => s.departmentId === Number(params.departmentId));
            }
            if (params.minGpa != null && params.minGpa > 0) {
              items = items.filter(s => (s.gpa ?? 3.5) >= params.minGpa!);
            }

            const totalItems = res.data.studentsCount ?? items.length;
            const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
            return {
              items,
              pagination: {
                currentPage: page,
                pageSize,
                totalItems,
                totalPages,
              },
              departments: res.data.departments,
            };
          }
        } catch (err) {
          console.error('getStudentsPaginated GraphQL error:', err);
        }
      } else {
        const qp = new URLSearchParams();
        qp.set('page', String(page));
        qp.set('pageSize', String(pageSize));
        if (params.search) qp.set('search', params.search);
        if (params.departmentId && params.departmentId !== 'all') qp.set('departmentId', String(params.departmentId));
        if (params.minGpa != null && params.minGpa > 0) qp.set('minGpa', String(params.minGpa));
        if (params.sortBy) qp.set('sortBy', params.sortBy);
        if (params.sortOrder) qp.set('sortOrder', params.sortOrder);

        try {
          const res = await fetch(`${this.baseUrl}/students?${qp.toString()}`, {
            headers: this.getHeaders(),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.items && typeof data.totalItems === 'number') {
              return {
                items: data.items.map((s: Student) => ({
                  ...s,
                  department: this.departments.find((d) => d.id === s.departmentId) || s.department || null,
                  enrollments: s.enrollments || this.enrollments.filter((e) => e.studentId === s.id).map((e) => ({
                    ...e,
                    course: this.courses.find((c) => c.id === e.courseId) || null,
                  })),
                })),
                pagination: {
                  currentPage: data.currentPage,
                  pageSize: data.pageSize,
                  totalItems: data.totalItems,
                  totalPages: data.totalPages,
                },
              };
            }
          }
        } catch (err) {
          console.error('getStudentsPaginated network error:', err);
        }
      }
    }

    let filtered = this.students.filter(s => {
      if (params.search) {
        const term = params.search.toLowerCase();
        if (!s.name.toLowerCase().includes(term) && !s.email.toLowerCase().includes(term)) {
          return false;
        }
      }
      if (params.departmentId && params.departmentId !== 'all') {
        if (s.departmentId !== Number(params.departmentId)) return false;
      }
      if (params.minGpa != null && params.minGpa > 0) {
        if ((s.gpa ?? 3.5) < params.minGpa) return false;
      }
      return true;
    });

    if (params.sortBy) {
      filtered = filtered.sort((a, b) => {
        const isDesc = params.sortOrder === 'desc';
        if (params.sortBy === 'gpa') {
          const gpaA = a.gpa ?? 3.5;
          const gpaB = b.gpa ?? 3.5;
          return isDesc ? gpaB - gpaA : gpaA - gpaB;
        } else if (params.sortBy === 'id') {
          return isDesc ? b.id - a.id : a.id - b.id;
        }
        return isDesc ? b.name.localeCompare(a.name) : a.name.localeCompare(b.name);
      });
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const items = filtered.slice((page - 1) * pageSize, page * pageSize).map((s) => ({
      ...s,
      department: this.departments.find((d) => d.id === s.departmentId) || null,
      enrollments: this.enrollments.filter((e) => e.studentId === s.id).map((e) => ({
        ...e,
        course: this.courses.find((c) => c.id === e.courseId) || null,
      })),
    }));

    return {
      items,
      pagination: { currentPage: page, pageSize, totalItems, totalPages },
    };
  }

  public async getStudents(): Promise<Student[]> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const query = `
          query GetStudents {
            students {
              id
              name
              email
              gpa
              departmentId
              department {
                id
                name
                code
              }
              enrollments {
                id
                grade
                gradePoints
                course {
                  id
                  title
                  code
                  credits
                }
              }
            }
          }
        `;
        const res = await this.executeGraphQl<{ students: Student[] }>(query);
        if (res.data?.students) return res.data.students;
      } else {
        const res = await fetch(`${this.baseUrl}/students`, {
          headers: this.getHeaders(),
        });
        if (res.ok) {
          const list: Student[] = await res.json();
          return list.map((s) => ({
            ...s,
            department: this.departments.find((d) => d.id === s.departmentId) || null,
          }));
        }
      }
    }

    return this.students.map((s) => ({
      ...s,
      department: this.departments.find((d) => d.id === s.departmentId) || null,
      enrollments: this.enrollments.filter((e) => e.studentId === s.id).map((e) => ({
        ...e,
        course: this.courses.find((c) => c.id === e.courseId) || null,
      })),
    }));
  }

  public async getStudentById(id: number): Promise<Student | null> {
    const list = await this.getStudents();
    return list.find((s) => s.id === Number(id)) || null;
  }

  public async createStudent(input: CreateStudentInput): Promise<Student> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const mutation = `
          mutation CreateStudent($input: StudentInput!) {
            createStudent(input: $input) {
              id
              name
              email
              gpa
              departmentId
            }
          }
        `;
        const res = await this.executeGraphQl<{ createStudent: Student }>(mutation, {
          input: {
            name: input.name,
            email: input.email,
            gpa: input.gpa ?? 3.5,
            departmentId: input.departmentId ?? null,
          },
        });
        if (res.data?.createStudent) {
          this.students.push(res.data.createStudent);
          this.notify();
          return res.data.createStudent;
        }
      } else {
        const res = await fetch(`${this.baseUrl}/students`, {
          method: 'POST',
          headers: this.getHeaders({ 'Content-Type': 'application/json' }),
          body: JSON.stringify(input),
        });
        if (res.ok) {
          const created = await res.json();
          this.students.push(created);
          this.notify();
          return created;
        }
      }
    }

    const newId = Math.max(...this.students.map((s) => s.id), 0) + 1;
    const newStudent: Student = {
      id: newId,
      name: input.name,
      email: input.email,
      gpa: input.gpa ?? 3.5,
      departmentId: input.departmentId ?? null,
      department: this.departments.find((d) => d.id === input.departmentId) || null,
      enrollments: [],
    };
    this.students.push(newStudent);
    this.logActivity('student_created', `Student ${input.name} matriculated`, `Registered to ${newStudent.department?.name || 'General Studies'}`, 'Admissions');
    this.notify();
    return newStudent;
  }

  public async updateStudent(id: number, input: UpdateStudentInput): Promise<Student> {
    if (this.backendConnected) {
      const res = await fetch(`${this.baseUrl}/students/${id}`, {
        method: 'PUT',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      });
      if (res.ok) {
        const updated = await res.json();
        const idx = this.students.findIndex((s) => s.id === id);
        if (idx !== -1) this.students[idx] = updated;
        this.notify();
        return updated;
      }
    }
    const idx = this.students.findIndex((s) => s.id === id);
    if (idx === -1) throw new Error('Student not found');
    this.students[idx] = {
      ...this.students[idx],
      ...input,
      department: input.departmentId !== undefined
        ? this.departments.find((d) => d.id === input.departmentId) || null
        : this.students[idx].department,
    };
    this.notify();
    return this.students[idx];
  }

  public async deleteStudent(id: number): Promise<boolean> {
    if (this.backendConnected) {
      await fetch(`${this.baseUrl}/students/${id}`, {
        method: 'DELETE',
        headers: this.getHeaders(),
      });
    }
    this.students = this.students.filter((s) => s.id !== id);
    this.enrollments = this.enrollments.filter((e) => e.studentId !== id);
    this.notify();
    return true;
  }

  // ==========================================
  // COURSES
  // ==========================================

  public async getCoursesPaginated(params: QueryOptions = {}): Promise<PaginatedResult<Course>> {
    const page = Math.max(1, params.page || 1);
    const pageSize = Math.max(1, params.pageSize || 6);

    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const offset = (page - 1) * pageSize;
        const query = `
          query GetCoursesPage($limit: Int, $offset: Int) {
            courses(limit: $limit, offset: $offset) {
              id
              title
              code
              credits
              departmentId
              department {
                id
                name
                code
              }
              instructorId
              instructor {
                id
                name
                email
              }
            }
            coursesCount
            departments {
              id
              name
              code
            }
            instructors {
              id
              name
              email
            }
          }
        `;
        try {
          const res = await this.executeGraphQl<{
            courses: Course[];
            coursesCount?: number;
            departments?: Department[];
            instructors?: Instructor[];
          }>(query, { limit: pageSize, offset });
          if (res.data?.courses) {
            let items = res.data.courses.map((c) => ({
              ...c,
              department: c.department || this.departments.find((d) => d.id === c.departmentId) || null,
              instructor: c.instructor || this.instructors.find((i) => i.id === c.instructorId) || null,
            }));

            if (params.search) {
              const term = params.search.toLowerCase();
              items = items.filter(c => c.title.toLowerCase().includes(term) || c.code.toLowerCase().includes(term));
            }
            if (params.departmentId && params.departmentId !== 'all') {
              items = items.filter(c => c.departmentId === Number(params.departmentId));
            }
            if (params.instructorId && params.instructorId !== 'all') {
              items = items.filter(c => c.instructorId === Number(params.instructorId));
            }
            if (params.credits && params.credits !== 'all') {
              items = items.filter(c => c.credits === Number(params.credits));
            }

            const totalItems = res.data.coursesCount ?? items.length;
            const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
            return {
              items,
              pagination: {
                currentPage: page,
                pageSize,
                totalItems,
                totalPages,
              },
              departments: res.data.departments,
              instructors: res.data.instructors,
            };
          }
        } catch (err) {
          console.error('getCoursesPaginated GraphQL error:', err);
        }
      } else {
        const qp = new URLSearchParams();
        qp.set('page', String(page));
        qp.set('pageSize', String(pageSize));
        if (params.search) qp.set('search', params.search);
        if (params.departmentId && params.departmentId !== 'all') qp.set('departmentId', String(params.departmentId));
        if (params.instructorId && params.instructorId !== 'all') qp.set('instructorId', String(params.instructorId));
        if (params.credits && params.credits !== 'all') qp.set('credits', String(params.credits));
        if (params.sortBy) qp.set('sortBy', params.sortBy);
        if (params.sortOrder) qp.set('sortOrder', params.sortOrder);

        try {
          const res = await fetch(`${this.baseUrl}/courses?${qp.toString()}`, {
            headers: this.getHeaders(),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.items && typeof data.totalItems === 'number') {
              return {
                items: data.items.map((c: Course) => ({
                  ...c,
                  department: this.departments.find((d) => d.id === c.departmentId) || c.department || null,
                  instructor: this.instructors.find((i) => i.id === c.instructorId) || c.instructor || null,
                })),
                pagination: {
                  currentPage: data.currentPage,
                  pageSize: data.pageSize,
                  totalItems: data.totalItems,
                  totalPages: data.totalPages,
                },
              };
            }
          }
        } catch (err) {
          console.error('getCoursesPaginated network error:', err);
        }
      }
    }

    let filtered = this.courses.filter(c => {
      if (params.search) {
        const term = params.search.toLowerCase();
        if (!c.title.toLowerCase().includes(term) && !c.code.toLowerCase().includes(term)) return false;
      }
      if (params.departmentId && params.departmentId !== 'all') {
        if (c.departmentId !== Number(params.departmentId)) return false;
      }
      if (params.instructorId && params.instructorId !== 'all') {
        if (c.instructorId !== Number(params.instructorId)) return false;
      }
      if (params.credits && params.credits !== 'all') {
        if (c.credits !== Number(params.credits)) return false;
      }
      return true;
    });

    if (params.sortBy) {
      filtered = filtered.sort((a, b) => {
        const isDesc = params.sortOrder === 'desc';
        if (params.sortBy === 'title') {
          return isDesc ? b.title.localeCompare(a.title) : a.title.localeCompare(b.title);
        } else if (params.sortBy === 'credits') {
          return isDesc ? b.credits - a.credits : a.credits - b.credits;
        } else if (params.sortBy === 'id') {
          return isDesc ? b.id - a.id : a.id - b.id;
        }
        return isDesc ? b.code.localeCompare(a.code) : a.code.localeCompare(b.code);
      });
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const items = filtered.slice((page - 1) * pageSize, page * pageSize).map((c) => ({
      ...c,
      department: this.departments.find((d) => d.id === c.departmentId) || null,
      instructor: this.instructors.find((i) => i.id === c.instructorId) || null,
    }));

    return {
      items,
      pagination: { currentPage: page, pageSize, totalItems, totalPages },
    };
  }

  public async getCourses(): Promise<Course[]> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const query = `
          query GetCourses {
            courses {
              id
              title
              code
              credits
              departmentId
              department {
                id
                name
                code
              }
              instructorId
              instructor {
                id
                name
                email
              }
            }
          }
        `;
        const res = await this.executeGraphQl<{ courses: Course[] }>(query);
        if (res.data?.courses) return res.data.courses;
      } else {
        const res = await fetch(`${this.baseUrl}/courses`, {
          headers: this.getHeaders(),
        });
        if (res.ok) return await res.json();
      }
    }

    return this.courses.map((c) => ({
      ...c,
      department: this.departments.find((d) => d.id === c.departmentId) || null,
      instructor: this.instructors.find((i) => i.id === c.instructorId) || null,
      enrollments: this.enrollments.filter((e) => e.courseId === c.id),
    }));
  }

  public async createCourse(input: CreateCourseInput): Promise<Course> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const mutation = `
          mutation CreateCourse($input: CourseInput!) {
            createCourse(input: $input) {
              id
              title
              code
              credits
              departmentId
              instructorId
            }
          }
        `;
        const res = await this.executeGraphQl<{ createCourse: Course }>(mutation, {
          input: {
            title: input.title,
            code: input.code,
            credits: input.credits,
            departmentId: input.departmentId,
            instructorId: input.instructorId ?? null,
          },
        });
        if (res.data?.createCourse) {
          this.courses.push(res.data.createCourse);
          this.notify();
          return res.data.createCourse;
        }
      } else {
        const res = await fetch(`${this.baseUrl}/courses`, {
          method: 'POST',
          headers: this.getHeaders({ 'Content-Type': 'application/json' }),
          body: JSON.stringify(input),
        });
        if (res.ok) {
          const created = await res.json();
          this.courses.push(created);
          this.notify();
          return created;
        }
      }
    }

    const newId = Math.max(...this.courses.map((c) => c.id), 0) + 1;
    const newCourse: Course = {
      id: newId,
      ...input,
      department: this.departments.find((d) => d.id === input.departmentId) || null,
      instructor: this.instructors.find((i) => i.id === input.instructorId) || null,
    };
    this.courses.push(newCourse);
    this.logActivity('course_added', `Course ${input.code} - ${input.title} published`, `${input.credits} Credits assigned`, 'Curriculum');
    this.notify();
    return newCourse;
  }

  public async updateCourse(id: number, input: UpdateCourseInput): Promise<Course> {
    if (this.backendConnected) {
      const res = await fetch(`${this.baseUrl}/courses/${id}`, {
        method: 'PUT',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      });
      if (res.ok) {
        const updated = await res.json();
        const idx = this.courses.findIndex((c) => c.id === id);
        if (idx !== -1) this.courses[idx] = updated;
        this.notify();
        return updated;
      }
    }
    const idx = this.courses.findIndex((c) => c.id === id);
    if (idx === -1) throw new Error('Course not found');
    this.courses[idx] = {
      ...this.courses[idx],
      ...input,
      department: input.departmentId ? this.departments.find((d) => d.id === input.departmentId) || null : this.courses[idx].department,
      instructor: input.instructorId ? this.instructors.find((i) => i.id === input.instructorId) || null : this.courses[idx].instructor,
    };
    this.notify();
    return this.courses[idx];
  }

  public async deleteCourse(id: number): Promise<boolean> {
    if (this.backendConnected) {
      await fetch(`${this.baseUrl}/courses/${id}`, {
        method: 'DELETE',
        headers: this.getHeaders(),
      });
    }
    this.courses = this.courses.filter((c) => c.id !== id);
    this.enrollments = this.enrollments.filter((e) => e.courseId !== id);
    this.notify();
    return true;
  }

  // ==========================================
  // INSTRUCTORS
  // ==========================================

  public async getInstructors(): Promise<Instructor[]> {
    if (this.backendConnected && this.mode === 'graphql') {
      const query = `
        query GetInstructors {
          instructors {
            id
            name
            email
            departmentId
            department {
              id
              name
              code
            }
          }
        }
      `;
      const res = await this.executeGraphQl<{ instructors: Instructor[] }>(query);
      if (res.data?.instructors) return res.data.instructors;
    }
    return this.instructors.map((i) => ({
      ...i,
      department: this.departments.find((d) => d.id === i.departmentId) || null,
      courses: this.courses.filter((c) => c.instructorId === i.id),
    }));
  }

  // ==========================================
  // ENROLLMENTS
  // ==========================================

  public async getEnrollmentsPaginated(params: QueryOptions = {}): Promise<PaginatedResult<Enrollment>> {
    const page = Math.max(1, params.page || 1);
    const pageSize = Math.max(1, params.pageSize || 8);

    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const offset = (page - 1) * pageSize;
        const query = `
          query GetEnrollmentsPage($limit: Int, $offset: Int) {
            enrollments(limit: $limit, offset: $offset) {
              id
              studentId
              student {
                id
                name
                email
              }
              courseId
              course {
                id
                title
                code
                credits
              }
              grade
              gradePoints
              enrollmentDate
            }
            enrollmentsCount
            courses {
              id
              title
              code
            }
            students(limit: 50) {
              id
              name
            }
          }
        `;
        try {
          const res = await this.executeGraphQl<{
            enrollments: Enrollment[];
            enrollmentsCount?: number;
            courses?: Course[];
            students?: Student[];
          }>(query, { limit: pageSize, offset });
          if (res.data?.enrollments) {
            let items = res.data.enrollments.map((e) => ({
              ...e,
              student: e.student || this.students.find((s) => s.id === e.studentId) || null,
              course: e.course || this.courses.find((c) => c.id === e.courseId) || null,
            }));

            if (params.courseId && params.courseId !== 'all') {
              items = items.filter(e => e.courseId === Number(params.courseId));
            }
            if (params.grade && params.grade !== 'all') {
              items = items.filter(e => (e.grade ?? '').startsWith(params.grade!));
            }
            if (params.search) {
              const term = params.search.toLowerCase();
              items = items.filter(e => {
                const sMatch = e.student?.name.toLowerCase().includes(term) ?? false;
                const cMatch = (e.course?.title.toLowerCase().includes(term) || e.course?.code.toLowerCase().includes(term)) ?? false;
                return sMatch || cMatch;
              });
            }

            const totalItems = res.data.enrollmentsCount ?? items.length;
            const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
            return {
              items,
              pagination: {
                currentPage: page,
                pageSize,
                totalItems,
                totalPages,
              },
              courses: res.data.courses,
              students: res.data.students,
            };
          }
        } catch (err) {
          console.error('getEnrollmentsPaginated GraphQL error:', err);
        }
      } else {
        const qp = new URLSearchParams();
        qp.set('page', String(page));
        qp.set('pageSize', String(pageSize));
        if (params.courseId && params.courseId !== 'all') qp.set('courseId', String(params.courseId));
        if (params.grade && params.grade !== 'all') qp.set('grade', params.grade);
        if (params.sortBy) qp.set('sortBy', params.sortBy);
        if (params.sortOrder) qp.set('sortOrder', params.sortOrder);

        try {
          const res = await fetch(`${this.baseUrl}/enrollments?${qp.toString()}`, {
            headers: this.getHeaders(),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.items && typeof data.totalItems === 'number') {
              return {
                items: data.items.map((e: Enrollment) => ({
                  ...e,
                  student: this.students.find((s) => s.id === e.studentId) || e.student || null,
                  course: this.courses.find((c) => c.id === e.courseId) || e.course || null,
                })),
                pagination: {
                  currentPage: data.currentPage,
                  pageSize: data.pageSize,
                  totalItems: data.totalItems,
                  totalPages: data.totalPages,
                },
              };
            }
          }
        } catch (err) {
          console.error('getEnrollmentsPaginated network error:', err);
        }
      }
    }

    let filtered = this.enrollments.filter(e => {
      if (params.courseId && params.courseId !== 'all') {
        if (e.courseId !== Number(params.courseId)) return false;
      }
      if (params.grade && params.grade !== 'all') {
        if (!(e.grade ?? '').startsWith(params.grade)) return false;
      }
      if (params.search) {
        const term = params.search.toLowerCase();
        const student = this.students.find(s => s.id === e.studentId);
        const course = this.courses.find(c => c.id === e.courseId);
        const sMatch = student?.name.toLowerCase().includes(term) ?? false;
        const cMatch = (course?.title.toLowerCase().includes(term) || course?.code.toLowerCase().includes(term)) ?? false;
        if (!sMatch && !cMatch) return false;
      }
      return true;
    });

    if (params.sortBy) {
      filtered = filtered.sort((a, b) => {
        const isDesc = params.sortOrder === 'desc';
        if (params.sortBy === 'grade') {
          return isDesc ? (b.grade ?? '').localeCompare(a.grade ?? '') : (a.grade ?? '').localeCompare(b.grade ?? '');
        } else if (params.sortBy === 'date') {
          return isDesc ? (b.enrollmentDate ?? '').localeCompare(a.enrollmentDate ?? '') : (a.enrollmentDate ?? '').localeCompare(b.enrollmentDate ?? '');
        }
        return isDesc ? b.id - a.id : a.id - b.id;
      });
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const items = filtered.slice((page - 1) * pageSize, page * pageSize).map((e) => ({
      ...e,
      student: this.students.find((s) => s.id === e.studentId) || null,
      course: this.courses.find((c) => c.id === e.courseId) || null,
    }));

    return {
      items,
      pagination: { currentPage: page, pageSize, totalItems, totalPages },
    };
  }

  public async getEnrollments(): Promise<Enrollment[]> {
    if (this.backendConnected && this.mode === 'graphql') {
      const query = `
        query GetEnrollments {
          enrollments {
            id
            studentId
            student {
              id
              name
              email
            }
            courseId
            course {
              id
              title
              code
              credits
            }
            grade
            gradePoints
            enrollmentDate
          }
        }
      `;
      const res = await this.executeGraphQl<{ enrollments: Enrollment[] }>(query);
      if (res.data?.enrollments) return res.data.enrollments;
    }

    return this.enrollments.map((e) => ({
      ...e,
      student: this.students.find((s) => s.id === e.studentId) || null,
      course: this.courses.find((c) => c.id === e.courseId) || null,
    }));
  }

  public async createEnrollment(input: CreateEnrollmentInput): Promise<Enrollment> {
    if (this.backendConnected) {
      const res = await fetch(`${this.baseUrl}/enrollments`, {
        method: 'POST',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      });
      if (res.ok) {
        const created = await res.json();
        this.enrollments.push(created);
        this.notify();
        return created;
      }
    }

    const newId = Math.max(...this.enrollments.map((e) => e.id), 0) + 1;
    const student = this.students.find((s) => s.id === input.studentId);
    const course = this.courses.find((c) => c.id === input.courseId);

    const newEnrollment: Enrollment = {
      id: newId,
      studentId: input.studentId,
      courseId: input.courseId,
      grade: input.grade || 'In Progress',
      gradePoints: input.gradePoints ?? (input.grade === 'A' ? 4.0 : input.grade === 'B' ? 3.0 : 0.0),
      enrollmentDate: input.enrollmentDate || new Date().toISOString().split('T')[0],
      student,
      course,
    };
    this.enrollments.push(newEnrollment);
    this.logActivity(
      'enrollment',
      `${student?.name || 'Student'} enrolled in ${course?.code || 'Course'}`,
      `Semester registration confirmed`,
      'Enrollment'
    );
    this.notify();
    return newEnrollment;
  }

  public async updateEnrollment(id: number, input: UpdateEnrollmentInput): Promise<Enrollment> {
    if (this.backendConnected) {
      const res = await fetch(`${this.baseUrl}/enrollments/${id}`, {
        method: 'PUT',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(input),
      });
      if (res.ok) {
        const updated = await res.json();
        const idx = this.enrollments.findIndex((e) => e.id === id);
        if (idx !== -1) this.enrollments[idx] = updated;
        this.notify();
        return updated;
      }
    }

    const idx = this.enrollments.findIndex((e) => e.id === id);
    if (idx === -1) throw new Error('Enrollment not found');
    this.enrollments[idx] = { ...this.enrollments[idx], ...input };
    if (input.grade) {
      const e = this.enrollments[idx];
      const s = this.students.find((st) => st.id === e.studentId);
      const c = this.courses.find((cr) => cr.id === e.courseId);
      this.logActivity('grade', `Grade recorded for ${s?.name || 'Student'}`, `${c?.code || 'Course'}: Grade ${input.grade}`, 'Gradebook');
    }
    this.notify();
    return this.enrollments[idx];
  }

  public async deleteEnrollment(id: number): Promise<boolean> {
    if (this.backendConnected) {
      await fetch(`${this.baseUrl}/enrollments/${id}`, {
        method: 'DELETE',
        headers: this.getHeaders(),
      });
    }
    this.enrollments = this.enrollments.filter((e) => e.id !== id);
    this.notify();
    return true;
  }

  // ==========================================
  // GRAPHORM VIEW MODELS
  // ==========================================

  /**
   * Stage 1: Database-backed physical view (DepartmentStats)
   */
  public async getDepartmentStats(): Promise<DepartmentStats[]> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const query = `
          query GetDepartmentStats {
            departmentStats {
              id
              departmentName
              studentCount
              courseCount
            }
          }
        `;
        const res = await this.executeGraphQl<{ departmentStats: DepartmentStats[] }>(query);
        if (res.data?.departmentStats) return res.data.departmentStats;
      } else {
        const res = await fetch(`${this.baseUrl}/stats`, {
          headers: this.getHeaders(),
        });
        if (res.ok) return await res.json();
      }
    }

    return this.departments.map((d) => {
      const studentCount = this.students.filter((s) => s.departmentId === d.id).length;
      const courseCount = this.courses.filter((c) => c.departmentId === d.id).length;
      return {
        id: d.id,
        departmentName: d.name,
        studentCount,
        courseCount,
      };
    });
  }

  /**
   * Stage 2: GraphORM Virtual Aggregated View (StudentDashboard)
   */
  public async getStudentDashboardsPaginated(params: QueryOptions = {}): Promise<PaginatedResult<StudentDashboard>> {
    const page = Math.max(1, params.page || 1);
    const pageSize = Math.max(1, params.pageSize || 5);

    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const offset = (page - 1) * pageSize;
        const query = `
          query GetAnalyticsPage($limit: Int, $offset: Int) {
            studentDashboards(limit: $limit, offset: $offset) {
              studentId
              studentName
              departmentName
              enrolledCourses
              avgGradePoints
            }
            studentDashboardsCount
            departmentStats {
              id
              departmentName
              studentCount
              courseCount
            }
          }
        `;
        try {
          const res = await this.executeGraphQl<{
            studentDashboards: StudentDashboard[];
            studentDashboardsCount?: number;
            departmentStats?: DepartmentStats[];
          }>(query, { limit: pageSize, offset });
          if (res.data?.studentDashboards) {
            let items = res.data.studentDashboards;

            if (params.search) {
              const term = params.search.toLowerCase();
              items = items.filter(s =>
                s.studentName.toLowerCase().includes(term) ||
                (s.departmentName?.toLowerCase().includes(term) ?? false)
              );
            }
            if (params.minCourses != null && params.minCourses > 0) {
              items = items.filter(s => s.enrolledCourses >= params.minCourses!);
            }

            const totalItems = res.data.studentDashboardsCount ?? items.length;
            const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
            return {
              items,
              pagination: {
                currentPage: page,
                pageSize,
                totalItems,
                totalPages,
              },
              departmentStats: res.data.departmentStats,
            };
          }
        } catch (err) {
          console.error('getStudentDashboardsPaginated GraphQL error:', err);
        }
      } else {
        const qp = new URLSearchParams();
        qp.set('page', String(page));
        qp.set('pageSize', String(pageSize));
        if (params.search) qp.set('search', params.search);
        if (params.minCourses != null && params.minCourses > 0) qp.set('minCourses', String(params.minCourses));
        if (params.sortBy) qp.set('sortBy', params.sortBy);
        if (params.sortOrder) qp.set('sortOrder', params.sortOrder);

        try {
          const res = await fetch(`${this.baseUrl}/dashboards?${qp.toString()}`, {
            headers: this.getHeaders(),
          });
          if (res.ok) {
            const data = await res.json();
            if (data.items && typeof data.totalItems === 'number') {
              return {
                items: data.items,
                pagination: {
                  currentPage: data.currentPage,
                  pageSize: data.pageSize,
                  totalItems: data.totalItems,
                  totalPages: data.totalPages,
                },
              };
            }
          }
        } catch (err) {
          console.error('getStudentDashboardsPaginated network error:', err);
        }
      }
    }

    const all = await this.getStudentDashboards();
    let filtered = all.filter(s => {
      if (params.search) {
        const term = params.search.toLowerCase();
        if (!s.studentName.toLowerCase().includes(term) && !(s.departmentName?.toLowerCase().includes(term) ?? false)) {
          return false;
        }
      }
      if (params.minCourses != null && params.minCourses > 0) {
        if (s.enrolledCourses < params.minCourses) return false;
      }
      return true;
    });

    if (params.sortBy) {
      filtered = filtered.sort((a, b) => {
        const isDesc = params.sortOrder === 'desc';
        if (params.sortBy === 'studentName') {
          return isDesc ? b.studentName.localeCompare(a.studentName) : a.studentName.localeCompare(b.studentName);
        } else if (params.sortBy === 'departmentName') {
          return isDesc ? (b.departmentName || '').localeCompare(a.departmentName || '') : (a.departmentName || '').localeCompare(b.departmentName || '');
        } else if (params.sortBy === 'enrolledCourses') {
          return isDesc ? b.enrolledCourses - a.enrolledCourses : a.enrolledCourses - b.enrolledCourses;
        }
        const gpaA = a.avgGradePoints ?? 0;
        const gpaB = b.avgGradePoints ?? 0;
        return isDesc ? gpaB - gpaA : gpaA - gpaB;
      });
    }

    const totalItems = filtered.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const items = filtered.slice((page - 1) * pageSize, page * pageSize);

    return {
      items,
      pagination: { currentPage: page, pageSize, totalItems, totalPages },
    };
  }

  public async getStudentDashboards(): Promise<StudentDashboard[]> {
    if (this.backendConnected) {
      if (this.mode === 'graphql') {
        const query = `
          query GetStudentDashboards {
            studentDashboards {
              studentId
              studentName
              departmentName
              enrolledCourses
              avgGradePoints
            }
          }
        `;
        const res = await this.executeGraphQl<{ studentDashboards: StudentDashboard[] }>(query);
        if (res.data?.studentDashboards) return res.data.studentDashboards;
      } else {
        const res = await fetch(`${this.baseUrl}/dashboards`, {
          headers: this.getHeaders(),
        });
        if (res.ok) return await res.json();
      }
    }

    return this.students.map((s) => {
      const dept = this.departments.find((d) => d.id === s.departmentId);
      const studentEnrolls = this.enrollments.filter((e) => e.studentId === s.id);
      const enrolledCourses = studentEnrolls.length;
      const points = studentEnrolls.filter((e) => e.gradePoints != null).map((e) => e.gradePoints as number);
      const avgGradePoints = points.length > 0
        ? Number((points.reduce((a, b) => a + b, 0) / points.length).toFixed(2))
        : null;

      return {
        studentId: s.id,
        studentName: s.name,
        departmentName: dept ? dept.name : null,
        enrolledCourses,
        avgGradePoints,
      };
    });
  }

  public async getActivities(): Promise<ActivityItem[]> {
    return [...this.activities];
  }

  private logActivity(type: ActivityItem['type'], title: string, description: string, badge: string): void {
    const act: ActivityItem = {
      id: `act-${Date.now()}`,
      timestamp: 'Just now',
      type,
      title,
      description,
      badge,
    };
    this.activities.unshift(act);
    if (this.activities.length > 20) this.activities.pop();
  }

  // ==========================================
  // GRAPHQL DIRECT PLAYGROUND EXECUTION
  // ==========================================

  public async getDashboardOverview(): Promise<{
    departments: Department[];
    instructors: Instructor[];
    students: Student[];
    courses: Course[];
    enrollments: Enrollment[];
    departmentStats: DepartmentStats[];
    activities: ActivityItem[];
  }> {
    if (this.backendConnected && this.mode === 'graphql') {
      const query = `
        query GetDashboardOverview {
          departments { id name code }
          instructors { id name email departmentId }
          students { id name email gpa departmentId }
          courses { id title code credits departmentId instructorId }
          enrollments { id studentId courseId grade gradePoints enrollmentDate }
          departmentStats { id departmentName studentCount courseCount }
        }
      `;
      try {
        const res = await this.executeGraphQl<{
          departments: Department[];
          instructors: Instructor[];
          students: Student[];
          courses: Course[];
          enrollments: Enrollment[];
          departmentStats: DepartmentStats[];
        }>(query);

        if (res.data) {
          return {
            departments: res.data.departments || [],
            instructors: res.data.instructors || [],
            students: res.data.students || [],
            courses: res.data.courses || [],
            enrollments: res.data.enrollments || [],
            departmentStats: res.data.departmentStats || [],
            activities: [...this.activities],
          };
        }
      } catch (err) {
        console.error('getDashboardOverview GraphQL error:', err);
      }
    }

    const [depts, insts, studs, crss, enrs, deptSts, acts] = await Promise.all([
      this.getDepartments(),
      this.getInstructors(),
      this.getStudents(),
      this.getCourses(),
      this.getEnrollments(),
      this.getDepartmentStats(),
      this.getActivities(),
    ]);
    return {
      departments: depts,
      instructors: insts,
      students: studs,
      courses: crss,
      enrollments: enrs,
      departmentStats: deptSts,
      activities: acts,
    };
  }

  public async executeGraphQl<T = any>(
    query: string,
    variables?: Record<string, any>
  ): Promise<{ data?: T; errors?: Array<{ message: string; extensions?: any }>; durationMs: number }> {
    const start = performance.now();
    try {
      if (this.backendConnected) {
        const res = await fetch(this.graphqlUrl, {
          method: 'POST',
          headers: this.getHeaders({
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify({ query, variables }),
        });
        const json = await res.json();
        const durationMs = Math.round(performance.now() - start);
        return { ...json, durationMs };
      }
    } catch {
      // Fallback
    }

    const durationMs = Math.round(performance.now() - start);
    return this.simulateClientGraphQl(query, durationMs);
  }

  private simulateClientGraphQl(query: string, durationMs: number): { data?: any; errors?: any[]; durationMs: number } {
    const clean = query.replace(/\s+/g, ' ');
    const data: Record<string, any> = {};

    if (clean.includes('students')) {
      data.students = this.students.map((s) => ({
        ...s,
        department: this.departments.find((d) => d.id === s.departmentId) || null,
        enrollments: this.enrollments.filter((e) => e.studentId === s.id).map((e) => ({
          ...e,
          course: this.courses.find((c) => c.id === e.courseId) || null,
        })),
      }));
    }

    if (clean.includes('departments')) {
      data.departments = this.departments.map((d) => ({
        ...d,
        courses: this.courses.filter((c) => c.departmentId === d.id),
      }));
    }

    if (clean.includes('courses')) {
      data.courses = this.courses.map((c) => ({
        ...c,
        department: this.departments.find((d) => d.id === c.departmentId) || null,
        instructor: this.instructors.find((i) => i.id === c.instructorId) || null,
      }));
    }

    if (clean.includes('departmentStats')) {
      data.departmentStats = this.departments.map((d) => ({
        id: d.id,
        departmentName: d.name,
        studentCount: this.students.filter((s) => s.departmentId === d.id).length,
        courseCount: this.courses.filter((c) => c.departmentId === d.id).length,
      }));
    }

    if (clean.includes('studentDashboards')) {
      data.studentDashboards = this.students.map((s) => {
        const dept = this.departments.find((d) => d.id === s.departmentId);
        const enrolls = this.enrollments.filter((e) => e.studentId === s.id);
        const pts = enrolls.filter((e) => e.gradePoints != null).map((e) => e.gradePoints as number);
        return {
          studentId: s.id,
          studentName: s.name,
          departmentName: dept?.name || null,
          enrolledCourses: enrolls.length,
          avgGradePoints: pts.length ? Number((pts.reduce((a, b) => a + b, 0) / pts.length).toFixed(2)) : null,
        };
      });
    }

    if (clean.includes('studentsCount')) {
      data.studentsCount = this.students.length;
    }
    if (clean.includes('departmentsCount')) {
      data.departmentsCount = this.departments.length;
    }
    if (clean.includes('coursesCount')) {
      data.coursesCount = this.courses.length;
    }
    if (clean.includes('enrollmentsCount')) {
      data.enrollmentsCount = this.enrollments.length;
    }
    if (clean.includes('studentDashboardsCount')) {
      data.studentDashboardsCount = this.students.length;
    }

    return { data, durationMs };
  }
}

export { UniversityDataService };
export const api = new UniversityDataService();
