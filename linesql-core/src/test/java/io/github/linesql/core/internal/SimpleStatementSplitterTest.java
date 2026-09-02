package io.github.linesql.core.internal;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SimpleStatementSplitterTest {
    private final SimpleStatementSplitter splitter = new SimpleStatementSplitter();

    @Test
    public void skipsCommentOnlyFragmentsAfterSemicolon() {
        List<String> statements = splitter.split(
                "select id from ods.users;\n"
                        + "-- select id from old_table\n"
                        + "-- where dt = '${yyyy-MM-dd}';");

        assertEquals(1, statements.size());
        assertEquals("select id from ods.users", statements.get(0));
    }

    @Test
    public void treatsEscapedLineBreaksAsWhitespaceOutsideQuotes() {
        List<String> statements = splitter.split(
                "select id,\\n name from ods.users where note = '\\n';");

        assertEquals(1, statements.size());
        assertEquals("select id,\n name from ods.users where note = '\\n'", statements.get(0));
    }

    @Test
    public void keepsSemicolonsInsideDollarQuotedBlocks() {
        List<String> statements = splitter.split(
                "create function f(int) returns int as $$\n"
                        + "def f(x):\n"
                        + "    y = x + 1;\n"
                        + "    return y\n"
                        + "$$;\n"
                        + "select id from ods.users;");

        assertEquals(2, statements.size());
        assertEquals("select id from ods.users", statements.get(1));
    }
}
