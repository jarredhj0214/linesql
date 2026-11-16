package io.github.linesql.core.internal;

import io.github.linesql.core.model.SqlDialect;
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
        List<String> statements = splitter.split(script, SqlDialect.SQLSERVER);
        assertEquals(2, statements.size());
        assertEquals(select, statements.get(0));
        assertEquals("select go from dbo.u", statements.get(1));
    }

    @Test
    public void keepsGoAsSqlOutsideSqlServerBatchMode() {
        String sql = "select\nGO\nfrom src";
        assertEquals(sql, splitter.split(sql).get(0));
    }
    @Test
    public void keepsFlinkStatementSetSemicolonsAndRecoversAtKnownStatements() {
        String set = "EXPLAIN PLAN FOR STATEMENT SET BEGIN "
                + "INSERT INTO dst SELECT CASE WHEN id > 0 THEN id ELSE 0 END FROM src; "
                + "/* second sink */ INSERT INTO dst2 SELECT id FROM src; END";
        assertEquals(set, splitter.split(set + "; SELECT id FROM src;").get(0));
        assertEquals(2, splitter.split(set + "; SELECT id FROM src;").size());

        String incomplete = "EXECUTE STATEMENT SET BEGIN INSERT INTO dst SELECT id FROM src";
        List<String> results = splitter.split(incomplete + "; SELECT id FROM src;");
        assertEquals(2, results.size());
        assertEquals(incomplete, results.get(0));
        assertEquals("SELECT id FROM src", results.get(1));
    }
    @Test
    public void preservesStandaloneMySqlExecutableCommentsInMySqlAndDefaultScripts() {
        String executable = "/*!80000 SELECT id FROM ods.t */";
        String script = "/* ordinary note */; /*!80000 */; " + executable + ";";
        assertEquals(1, splitter.split(script).size());
        assertEquals(executable, splitter.split(script, SqlDialect.MYSQL).get(0));
        assertEquals(0, splitter.split(script, SqlDialect.POSTGRESQL).size());
    }
    @Test
    public void splitsOracleBlocksAtStandaloneSlashAndKeepsDivision() {
        String block = "BEGIN v := 'text\n/\ninside'; NULL; END;";
        String division = "SELECT 10\n/\n2 AS ratio FROM dual";
        List<String> statements = splitter.split(block + "\n/\n" + division + ";\n/\n", SqlDialect.ORACLE);
        assertEquals(2, statements.size());
        assertEquals(block, statements.get(0));
        assertEquals(division, statements.get(1));
    }
}
