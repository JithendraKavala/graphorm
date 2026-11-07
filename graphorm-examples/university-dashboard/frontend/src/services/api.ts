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
    return '';
  }
  return encodeAuth(envAuth);
}

class UniversityDataService {
  private mode: DataSourceMode = 'graphql';
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

    return { data, durationMs };
  }
}

export { UniversityDataService };
export const api = new UniversityDataService();
