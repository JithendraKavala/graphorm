-- Seed Departments
INSERT INTO departments (id, dept_name, code) VALUES
(1, 'Computer Science', 'CS'),
(2, 'Mathematics', 'MATH'),
(3, 'Physics', 'PHYS');

-- Seed Instructors
INSERT INTO instructors (id, name, email, salary, department_id) VALUES
(1, 'Alan Turing', 'turing@university.edu', 125000.0, 1),
(2, 'Ada Lovelace', 'lovelace@university.edu', 130000.0, 1),
(3, 'Carl Friedrich Gauss', 'gauss@university.edu', 115000.0, 2),
(4, 'Richard Feynman', 'feynman@university.edu', 120000.0, 3);

-- Seed Students
INSERT INTO students (id, name, email, gpa, department_id) VALUES
(1, 'Grace Hopper', 'hopper@student.edu', 3.95, 1),
(2, 'Claude Shannon', 'shannon@student.edu', 3.90, 1),
(3, 'John von Neumann', 'neumann@student.edu', 4.00, 2),
(4, 'Emmy Noether', 'noether@student.edu', 3.98, 2),
(5, 'Marie Curie', 'curie@student.edu', 3.92, 3),
(6, 'Independent Scholar', 'scholar@student.edu', 3.50, NULL);

-- Seed Courses
INSERT INTO courses (id, title, code, credits, department_id, instructor_id) VALUES
(1, 'Intro to Computer Science', 'CS101', 4, 1, 1),
(2, 'Compilers and Language Design', 'CS201', 4, 1, 2),
(3, 'Linear Algebra & Calculus', 'MATH101', 3, 2, 3),
(4, 'Quantum Mechanics', 'PHYS101', 4, 3, 4);

-- Seed Enrollments
INSERT INTO enrollments (id, student_id, course_id, grade, grade_points, enrollment_date) VALUES
(1, 1, 1, 'A', 4.0, '2026-09-01'),
(2, 1, 2, 'A', 4.0, '2026-09-01'),
(3, 2, 1, 'A-', 3.7, '2026-09-01'),
(4, 3, 3, 'A', 4.0, '2026-09-01'),
(5, 4, 3, 'A', 4.0, '2026-09-01'),
(6, 5, 4, 'A', 4.0, '2026-09-01');

-- Reset sequences
SELECT setval('departments_id_seq', 3);
SELECT setval('instructors_id_seq', 4);
SELECT setval('students_id_seq', 6);
SELECT setval('courses_id_seq', 4);
SELECT setval('enrollments_id_seq', 6);
