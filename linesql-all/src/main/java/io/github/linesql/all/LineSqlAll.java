package io.github.linesql.all;

/**
 * Marker type for the LineSQL aggregate artifact.
 *
 * <p>Applications usually do not need to use this class directly. Depend on
 * {@code linesql-all} when you want {@code io.github.linesql.core.LineSql} to
 * discover every bundled dialect parser from the runtime classpath.</p>
 */
public final class LineSqlAll {
    private LineSqlAll() {
    }
}
