package io.github.linesql.core.util;

import io.github.linesql.core.model.ColumnLineage;
import io.github.linesql.core.model.ColumnRef;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.TableRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LineageModelUtils {
    private LineageModelUtils() {
    }

    public static ColumnLineage columnLineage(TableRef targetTable,
                                              String targetColumn,
                                              List<ColumnRef> sources,
                                              String expression) {
        return columnLineage(new ColumnRef(targetTable, targetColumn), sources, expression);
    }

    public static ColumnLineage columnLineage(ColumnRef target,
                                              List<ColumnRef> sources,
                                              String expression) {
        ColumnLineage lineage = new ColumnLineage();
        lineage.setTarget(target);
        lineage.setSources(sources);
        lineage.setExpression(expression);
        return lineage;
    }

    public static List<ColumnLineage> retargetColumnLineage(List<ColumnLineage> lineage,
                                                            TableRef targetTable,
                                                            List<String> targetColumns) {
        List<ColumnLineage> retargeted = new ArrayList<ColumnLineage>();
        for (int i = 0; i < lineage.size(); i++) {
            ColumnLineage original = lineage.get(i);
            String name = i < targetColumns.size() ? targetColumns.get(i) : original.getTarget().getName();
            retargeted.add(columnLineage(targetTable, name, original.getSources(), original.getExpression()));
        }
        return retargeted;
    }

    public static List<ColumnLineage> mergeSetColumnLineage(LineageResult left, LineageResult right) {
        int size = Math.min(left.getColumnLineage().size(), right.getColumnLineage().size());
        List<ColumnLineage> merged = new ArrayList<ColumnLineage>();
        for (int i = 0; i < size; i++) {
            ColumnLineage leftColumn = left.getColumnLineage().get(i);
            ColumnLineage rightColumn = right.getColumnLineage().get(i);
            merged.add(columnLineage(
                    leftColumn.getTarget(),
                    mergeColumnRefs(leftColumn.getSources(), rightColumn.getSources()),
                    leftColumn.getExpression()));
        }
        return merged;
    }

    public static List<ColumnRef> mergeColumnRefs(List<ColumnRef> left, List<ColumnRef> right) {
        Map<String, ColumnRef> refs = new LinkedHashMap<String, ColumnRef>();
        for (ColumnRef ref : left) {
            refs.put(columnKey(ref), ref);
        }
        for (ColumnRef ref : right) {
            refs.put(columnKey(ref), ref);
        }
        return new ArrayList<ColumnRef>(refs.values());
    }

    public static String columnKey(ColumnRef ref) {
        TableRef table = ref.getTable();
        String tableKey = table == null ? ""
                : (table.getCatalog() == null ? "" : table.getCatalog()) + "."
                + (table.getSchema() == null ? "" : table.getSchema()) + "."
                + table.getName();
        return tableKey + "." + ref.getName();
    }

    public static TableRef tableRefFromParts(List<String> parts) {
        if (parts.size() >= 3) {
            return new TableRef(parts.get(parts.size() - 3), parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (parts.size() == 2) {
            return new TableRef(null, parts.get(0), parts.get(1));
        }
        return new TableRef(null, null, parts.get(0));
    }
}
