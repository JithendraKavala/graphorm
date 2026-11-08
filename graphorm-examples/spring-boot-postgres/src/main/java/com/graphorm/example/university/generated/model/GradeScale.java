package com.graphorm.example.university.generated.model;

/**
 * Immutable Java 21 model record representing the {@code GradeScale} entity (table {@code grade_scales}).
 */
public record GradeScale(
        java.lang.Integer id,
        java.lang.String letter,
        java.lang.Double minScore,
        java.lang.Double gpaPoint
) {
    public static final String MODEL_NAME = "GradeScale";

    public static final String TABLE_NAME = "grade_scales";

    /**
     * Creates a new {@link Builder} for {@link GradeScale}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of this {@link GradeScale} with the {@code id} component updated.
     */
    public GradeScale withId(Integer id) {
        return new GradeScale(id, this.letter, this.minScore, this.gpaPoint);
    }

    /**
     * Returns a copy of this {@link GradeScale} with the {@code letter} component updated.
     */
    public GradeScale withLetter(String letter) {
        return new GradeScale(this.id, letter, this.minScore, this.gpaPoint);
    }

    /**
     * Returns a copy of this {@link GradeScale} with the {@code minScore} component updated.
     */
    public GradeScale withMinScore(Double minScore) {
        return new GradeScale(this.id, this.letter, minScore, this.gpaPoint);
    }

    /**
     * Returns a copy of this {@link GradeScale} with the {@code gpaPoint} component updated.
     */
    public GradeScale withGpaPoint(Double gpaPoint) {
        return new GradeScale(this.id, this.letter, this.minScore, gpaPoint);
    }

    /**
     * Fluent builder for constructing {@link GradeScale} instances.
     */
    public static final class Builder {
        private Integer id;

        private String letter;

        private Double minScore;

        private Double gpaPoint;

        public Builder id(Integer id) {
            this.id = id;
            return this;
        }

        public Builder letter(String letter) {
            this.letter = letter;
            return this;
        }

        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder gpaPoint(Double gpaPoint) {
            this.gpaPoint = gpaPoint;
            return this;
        }

        public GradeScale build() {
            return new GradeScale(id, letter, minScore, gpaPoint);
        }
    }
}
