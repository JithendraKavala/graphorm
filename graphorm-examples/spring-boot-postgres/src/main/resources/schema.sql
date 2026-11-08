-- Drop objects in reverse dependency order
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
    department_id INT NOT NULL REFERENCES departments(id)
);

CREATE TABLE students (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    gpa DOUBLE PRECISION,
    department_id INT REFERENCES departments(id)
);

CREATE TABLE courses (
    id SERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    credits INT NOT NULL,
    department_id INT NOT NULL REFERENCES departments(id),
    instructor_id INT REFERENCES instructors(id)
);

CREATE TABLE enrollments (
    id SERIAL PRIMARY KEY,
    student_id INT NOT NULL REFERENCES students(id),
    course_id INT NOT NULL REFERENCES courses(id),
    grade VARCHAR(5),
    grade_points DOUBLE PRECISION,
    enrollment_date VARCHAR(20) NOT NULL
);

-- Read-Only Reference Model (Stage 15.9 @@readonly)
CREATE TABLE grade_scales (
    id SERIAL PRIMARY KEY,
    letter VARCHAR(10) NOT NULL UNIQUE,
    min_score DOUBLE PRECISION NOT NULL,
    gpa_point DOUBLE PRECISION NOT NULL
);

-- Model-Level Authorization & Backend-Injected Model (Stage 15.7 @@auth & 15.8 @injected)
CREATE TABLE audit_logs (
    id SERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    details VARCHAR(255),
    tenant_id VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL
);

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
