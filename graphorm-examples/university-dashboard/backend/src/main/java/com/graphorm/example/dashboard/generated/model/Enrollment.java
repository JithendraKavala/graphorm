package com.graphorm.example.dashboard.generated.model;

/**
 * Immutable Java 21 model record representing the {@code Enrollment} entity (table {@code enrollments}).
 */
public record Enrollment(
        java.lang.Integer id,
        java.lang.Integer studentId,
        java.lang.Integer courseId,
        java.lang.String grade,
        java.lang.Double gradePoints,
        java.lang.String enrollmentDate
) {
    public static final String MODEL_NAME = "Enrollment";

    public static final String TABLE_NAME = "enrollments";

    /**
     * Creates a new {@link Builder} for {@link Enrollment}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code id} component updated.
     */
    public Enrollment withId(Integer id) {
        return new Enrollment(id, this.studentId, this.courseId, this.grade, this.gradePoints, this.enrollmentDate);
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code studentId} component updated.
     */
    public Enrollment withStudentId(Integer studentId) {
        return new Enrollment(this.id, studentId, this.courseId, this.grade, this.gradePoints, this.enrollmentDate);
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code courseId} component updated.
     */
    public Enrollment withCourseId(Integer courseId) {
        return new Enrollment(this.id, this.studentId, courseId, this.grade, this.gradePoints, this.enrollmentDate);
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code grade} component updated.
     */
    public Enrollment withGrade(String grade) {
        return new Enrollment(this.id, this.studentId, this.courseId, grade, this.gradePoints, this.enrollmentDate);
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code gradePoints} component updated.
     */
    public Enrollment withGradePoints(Double gradePoints) {
        return new Enrollment(this.id, this.studentId, this.courseId, this.grade, gradePoints, this.enrollmentDate);
    }

    /**
     * Returns a copy of this {@link Enrollment} with the {@code enrollmentDate} component updated.
     */
    public Enrollment withEnrollmentDate(String enrollmentDate) {
        return new Enrollment(this.id, this.studentId, this.courseId, this.grade, this.gradePoints, enrollmentDate);
    }

    /**
     * Fluent builder for constructing {@link Enrollment} instances.
     */
    public static final class Builder {
        private Integer id;

        private Integer studentId;

        private Integer courseId;

        private String grade;

        private Double gradePoints;

        private String enrollmentDate;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder studentId(Integer studentId) {
            this.studentId = studentId;
            return this;
        }

        public Builder courseId(Integer courseId) {
            this.courseId = courseId;
            return this;
        }

        public Builder grade(String grade) {
            this.grade = grade;
            return this;
        }

        public Builder gradePoints(Double gradePoints) {
            this.gradePoints = gradePoints;
            return this;
        }

        public Builder enrollmentDate(String enrollmentDate) {
            this.enrollmentDate = enrollmentDate;
            return this;
        }

        public Enrollment build() {
            return new Enrollment(id, studentId, courseId, grade, gradePoints, enrollmentDate);
        }
    }
}
