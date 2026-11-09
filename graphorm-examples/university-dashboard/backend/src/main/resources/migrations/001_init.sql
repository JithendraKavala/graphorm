-- Migration 001: Initial schema for University Dashboard application
-- Drops and creates tables and database-backed views in topological order

DROP VIEW IF EXISTS v_department_stats CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS grade_scales CASCADE;
DROP TABLE IF EXISTS enrollments CASCADE;
DROP TABLE IF EXISTS courses CASCADE;
DROP TABLE IF EXISTS students CASCADE;
DROP TABLE IF EXISTS instructors CASCADE;
DROP TABLE IF EXISTS departments CASCADE;

-- Physical Tables
CREATE TABLE departments (
    id SERIAL PRIMARY KEY,
    dept_name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE instructors (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    salary DOUBLE PRECISION,
    department_id INT NOT NULL REFERENCES departments(id) ON DELETE CASCADE
);

CREATE TABLE students (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    gpa DOUBLE PRECISION,
    version INT NOT NULL DEFAULT 1,
    department_id INT REFERENCES departments(id) ON DELETE SET NULL
);

CREATE TABLE courses (
    id SERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    credits INT NOT NULL,
    department_id INT NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    instructor_id INT REFERENCES instructors(id) ON DELETE SET NULL
);

CREATE TABLE enrollments (
    id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id INT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    grade VARCHAR(5),
    grade_points DOUBLE PRECISION,
    enrollment_date VARCHAR(20) NOT NULL,
    CONSTRAINT uk_enrollment_student_course UNIQUE (student_id, course_id)
);

CREATE TABLE grade_scales (
    id SERIAL PRIMARY KEY,
    letter VARCHAR(5) NOT NULL UNIQUE,
    min_score DOUBLE PRECISION NOT NULL,
    gpa_point DOUBLE PRECISION NOT NULL
);

CREATE TABLE audit_logs (
    id SERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    tenant_id VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL
);

-- Indexes
CREATE INDEX idx_departments_code ON departments(code);
CREATE INDEX idx_instructors_department ON instructors(department_id);
CREATE INDEX idx_students_department ON students(department_id);
CREATE INDEX idx_students_email ON students(email);
CREATE INDEX idx_courses_department ON courses(department_id);
CREATE INDEX idx_courses_instructor ON courses(instructor_id);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
CREATE INDEX idx_audit_logs_tenant ON audit_logs(tenant_id);

-- Database-Backed Physical View (Stage 1 View Model)
CREATE OR REPLACE VIEW v_department_stats AS
SELECT
    d.id AS id,
    d.dept_name AS department_name,
    COUNT(DISTINCT s.id)::INT AS student_count,
    COUNT(DISTINCT c.id)::INT AS course_count
FROM departments d
LEFT JOIN students s ON s.department_id = d.id
LEFT JOIN courses c ON c.department_id = d.id
GROUP BY d.id, d.dept_name;
