-- Drop objects in reverse dependency order
DROP VIEW IF EXISTS v_department_stats CASCADE;
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
