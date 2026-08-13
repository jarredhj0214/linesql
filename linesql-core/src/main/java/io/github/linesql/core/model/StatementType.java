package io.github.linesql.core.model;

public enum StatementType {
    SELECT,
    INSERT,
    CREATE_TABLE_AS_SELECT,
    CREATE_TABLE_LIKE,
    CREATE_VIEW,
    DROP_VIEW,
    CACHE_TABLE,
    UNCACHE_TABLE,
    DROP_TABLE,
    TRUNCATE_TABLE,
    MERGE,
    UPDATE,
    DELETE,
    LOAD_DATA,
    UNKNOWN
}
