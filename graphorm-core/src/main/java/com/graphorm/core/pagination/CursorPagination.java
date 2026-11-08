package com.graphorm.core.pagination;

/**
 * Keyset cursor pagination specification containing forward (first, after) or backward (last, before) cursors.
 *
 * @param after  the opaque cursor after which records should be fetched
 * @param before the opaque cursor before which records should be fetched
 * @param first  maximum number of records to fetch after the cursor
 * @param last   maximum number of records to fetch before the cursor
 */
public record CursorPagination(
        String after,
        String before,
        Integer first,
        Integer last
) {
    public CursorPagination {
        if (first != null && first < 0) {
            throw new IllegalArgumentException("first limit cannot be negative: " + first);
        }
        if (last != null && last < 0) {
            throw new IllegalArgumentException("last limit cannot be negative: " + last);
        }
        if (first != null && last != null) {
            throw new IllegalArgumentException("Cannot specify both 'first' and 'last' in cursor pagination");
        }
        if (after != null && before != null) {
            throw new IllegalArgumentException("Cannot specify both 'after' and 'before' in cursor pagination");
        }
    }

    public boolean hasAfter() {
        return after != null && !after.isBlank();
    }

    public boolean hasBefore() {
        return before != null && !before.isBlank();
    }

    public boolean isForward() {
        return first != null || hasAfter();
    }

    public boolean isBackward() {
        return last != null || hasBefore();
    }

    public Integer limit() {
        return first != null ? first : last;
    }

    public static CursorPagination after(String after, int first) {
        return new CursorPagination(after, null, first, null);
    }

    public static CursorPagination before(String before, int last) {
        return new CursorPagination(null, before, null, last);
    }

    public static CursorPagination first(int first) {
        return new CursorPagination(null, null, first, null);
    }

    public static CursorPagination last(int last) {
        return new CursorPagination(null, null, null, last);
    }
}
