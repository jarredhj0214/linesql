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
    @Test
    public void keepsTaggedDollarQuotesAndMatchesTheirCase() {
        String block = "do $Body$ begin perform '$body$;'; perform '$$'; end; $Body$";
        List<String> statements = splitter.split(block + "; select $1;");
        assertEquals(2, statements.size());
        assertEquals(block, statements.get(0));
        assertEquals("select $1", statements.get(1));
    }

    @Test
    public void distinguishesDollarQuotesFromIdentifierSuffixes() {
        List<String> statements = splitter.split("select a$tag$ from ods.t; select $tag$x;y$tag$;");
        assertEquals(2, statements.size());
        assertEquals("select a$tag$ from ods.t", statements.get(0));
        assertEquals("select $tag$x;y$tag$", statements.get(1));
    }

    @Test
    public void splitsSqlServerGoLinesOutsideQuotedValuesAndComments() {
        String select = "select [a]];\r\nGO\r\nb], 'semi;\r\nGO\r\nquote''s' from dbo.t";
        String nestedComment = "/* outer /* inner */\r\nGO\r\n*/";
        String script = "GO\r\n" + select + "\r\n  go 002 -- repeat\r\n" + nestedComment
                + "\r\nGO\r\nselect go from dbo.u";
        List<String> statements = splitter.splitSqlServerBatches(script);
        assertEquals(2, statements.size());
        assertEquals(select, statements.get(0));
        assertEquals("select go from dbo.u", statements.get(1));
    }

    @Test
    public void keepsGoAsSqlOutsideSqlServerBatchMode() {
        String sql = "select\nGO\nfrom src";
        assertEquals(sql, splitter.split(sql).get(0));
    }
}
