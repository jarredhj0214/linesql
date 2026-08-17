package io.github.linesql.core.util;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.ColumnUsage;
import io.github.linesql.core.model.ColumnUsageType;
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

    @Test
    public void mergesColumnUsagesByTypeAndCaseInsensitiveColumnKey() {
        TableRef table = new TableRef(null, "ods", "users");
        LineageResult result = new LineageResult();
        result.getColumnUsages().add(new ColumnUsage(ColumnUsageType.WHERE, new ColumnRef(table, "ID")));

        LineageModelUtils.addColumnUsages(
                result,
                ColumnUsageType.WHERE,
                Arrays.asList(new ColumnRef(table, "id"), new ColumnRef(table, "name")));
        LineageModelUtils.addColumnUsages(
                result,
                ColumnUsageType.GROUP_BY,
                Arrays.asList(new ColumnRef(table, "id")));

        assertEquals(3, result.getColumnUsages().size());
        assertEquals(ColumnUsageType.WHERE, result.getColumnUsages().get(0).getType());
        assertEquals("id", result.getColumnUsages().get(0).getColumn().getName());
        assertEquals(ColumnUsageType.WHERE, result.getColumnUsages().get(1).getType());
        assertEquals("name", result.getColumnUsages().get(1).getColumn().getName());
        assertEquals(ColumnUsageType.GROUP_BY, result.getColumnUsages().get(2).getType());
        assertEquals("id", result.getColumnUsages().get(2).getColumn().getName());
    }
}
