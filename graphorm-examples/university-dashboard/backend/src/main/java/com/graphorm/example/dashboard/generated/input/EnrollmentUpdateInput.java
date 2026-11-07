package com.graphorm.example.dashboard.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Update input payload for {@link Enrollment}.
 */
public record EnrollmentUpdateInput(
        java.lang.Integer studentId,
        java.lang.Integer courseId,
        java.lang.String grade,
        java.lang.Double gradePoints,
        java.lang.String enrollmentDate
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.studentId != null) map.put("student_id", this.studentId);
        if (this.courseId != null) map.put("course_id", this.courseId);
        if (this.grade != null) map.put("grade", this.grade);
        if (this.gradePoints != null) map.put("grade_points", this.gradePoints);
        if (this.enrollmentDate != null) map.put("enrollment_date", this.enrollmentDate);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private Integer studentId;

        private Integer courseId;

        private String grade;

        private Double gradePoints;

        private String enrollmentDate;

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

        public EnrollmentUpdateInput build() {
            return new EnrollmentUpdateInput(studentId, courseId, grade, gradePoints, enrollmentDate);
        }
    }
}
