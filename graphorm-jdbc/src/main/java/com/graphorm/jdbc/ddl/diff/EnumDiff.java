package com.graphorm.jdbc.ddl.diff;

import java.util.List;
import java.util.Objects;

/**
 * Captures changes to an existing database enum type.
 *
 * @param enumName    the enum type name
 * @param addedValues list of newly declared enum values to add
 */
public record EnumDiff(
        String enumName,
        List<String> addedValues
) {

    public EnumDiff {
        Objects.requireNonNull(enumName, "Enum name must not be null");
        addedValues = addedValues == null ? List.of() : List.copyOf(addedValues);
    }

    public boolean hasChanges() {
        return !addedValues.isEmpty();
    }
}
