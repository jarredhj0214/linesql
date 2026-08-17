package io.github.linesql.core.util;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.TableRef;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LineageModelUtilsTest {
    @Test
    public void retargetsColumnLineageWithExplicitTargetColumns() {
        TableRef source = new TableRef(null, "ods", "users");
        TableRef target = new TableRef(null, "ads", "users");
        ColumnLineage original = LineageModelUtils.columnLineage(
                null,
                "id",
                Arrays.asList(new ColumnRef(source, "id")),
                "id");

        List<ColumnLineage> retargeted = LineageModelUtils.retargetColumnLineage(
                Arrays.asList(original),
                target,
                Arrays.asList("user_id"));

        assertEquals("user_id", retargeted.get(0).getTarget().getName());
        assertEquals(target, retargeted.get(0).getTarget().getTable());
        assertEquals(source, retargeted.get(0).getSources().get(0).getTable());
    }

    @Test
    public void mergesSetColumnLineageByPositionAndDeduplicatesSources() {
        TableRef leftTable = new TableRef(null, "ods", "a");
        TableRef rightTable = new TableRef(null, "ods", "b");
        ColumnLineage leftColumn = LineageModelUtils.columnLineage(
                null,
                "c1",
                Arrays.asList(new ColumnRef(leftTable, "id")),
                "id");
        ColumnLineage rightColumn = LineageModelUtils.columnLineage(
                null,
                "c1",
                Arrays.asList(new ColumnRef(leftTable, "id"), new ColumnRef(rightTable, "id")),
                "id");
        LineageResult left = new LineageResult();
        left.setColumnLineage(Arrays.asList(leftColumn));
        LineageResult right = new LineageResult();
        right.setColumnLineage(Arrays.asList(rightColumn));

        List<ColumnLineage> merged = LineageModelUtils.mergeSetColumnLineage(left, right);

        assertEquals(1, merged.size());
        assertEquals("c1", merged.get(0).getTarget().getName());
        assertEquals(2, merged.get(0).getSources().size());
    }

    @Test
    public void buildsTableRefFromRightmostIdentifierParts() {
        TableRef table = LineageModelUtils.tableRefFromParts(Arrays.asList("catalog", "db", "table"));

        assertEquals("catalog", table.getCatalog());
        assertEquals("db", table.getSchema());
        assertEquals("table", table.getName());
    }
}
