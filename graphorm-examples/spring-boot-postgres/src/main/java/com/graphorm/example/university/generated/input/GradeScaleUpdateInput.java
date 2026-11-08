package com.graphorm.example.university.generated.input;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Update input payload for {@link GradeScale}.
 */
public record GradeScaleUpdateInput(
        java.lang.String letter,
        java.lang.Double minScore,
        java.lang.Double gpaPoint
) {
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (this.letter != null) map.put("letter", this.letter);
        if (this.minScore != null) map.put("min_score", this.minScore);
        if (this.gpaPoint != null) map.put("gpa_point", this.gpaPoint);
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private String letter;

        private Double minScore;

        private Double gpaPoint;

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

        public GradeScaleUpdateInput build() {
            return new GradeScaleUpdateInput(letter, minScore, gpaPoint);
        }
    }
}
