package io.github.linesql.core.model;

public enum ColumnUsageType {
    WHERE,
    JOIN_ON,
    GROUP_BY,
    HAVING,
    ORDER_BY,
    WINDOW_PARTITION_BY,
    WINDOW_ORDER_BY,
    MERGE_ON,
    MERGE_WHEN
}
