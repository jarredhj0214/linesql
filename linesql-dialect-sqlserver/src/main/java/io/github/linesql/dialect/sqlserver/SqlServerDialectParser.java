package io.github.linesql.dialect.sqlserver;

import io.github.linesql.core.internal.SimpleTokenLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.sqlserver.antlr.SqlServerLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class SqlServerDialectParser implements DialectParser {
    private static final SimpleTokenLineageParser.Config CONFIG =
            SimpleTokenLineageParser.Config.forDialect(SqlDialect.SQLSERVER, "SQL Server", "SQLSERVER")
                    .select(SqlServerLineageLexer.SELECT)
                    .insert(SqlServerLineageLexer.INSERT)
                    .update(SqlServerLineageLexer.UPDATE)
                    .delete(SqlServerLineageLexer.DELETE)
                    .create(SqlServerLineageLexer.CREATE)
                    .overwrite(SqlServerLineageLexer.OVERWRITE)
                    .into(SqlServerLineageLexer.INTO)
                    .external(SqlServerLineageLexer.EXTERNAL)
                    .temporary(SqlServerLineageLexer.TEMPORARY)
                    .table(SqlServerLineageLexer.TABLE)
                    .view(SqlServerLineageLexer.VIEW)
                    .ifToken(SqlServerLineageLexer.IF)
                    .not(SqlServerLineageLexer.NOT)
                    .exists(SqlServerLineageLexer.EXISTS)
                    .as(SqlServerLineageLexer.AS)
                    .set(SqlServerLineageLexer.SET)
                    .with(SqlServerLineageLexer.WITH)
                    .from(SqlServerLineageLexer.FROM)
                    .using(SqlServerLineageLexer.USING)
                    .join(SqlServerLineageLexer.JOIN)
                    .inner(SqlServerLineageLexer.INNER)
                    .left(SqlServerLineageLexer.LEFT)
                    .right(SqlServerLineageLexer.RIGHT)
                    .full(SqlServerLineageLexer.FULL)
                    .cross(SqlServerLineageLexer.CROSS)
                    .outer(SqlServerLineageLexer.OUTER)
                    .on(SqlServerLineageLexer.ON)
                    .where(SqlServerLineageLexer.WHERE)
                    .group(SqlServerLineageLexer.GROUP)
                    .having(SqlServerLineageLexer.HAVING)
                    .order(SqlServerLineageLexer.ORDER)
                    .limit(SqlServerLineageLexer.LIMIT)
                    .union(SqlServerLineageLexer.UNION)
                    .partition(SqlServerLineageLexer.PARTITION)
                    .stored(SqlServerLineageLexer.STORED)
                    .row(SqlServerLineageLexer.ROW)
                    .identifier(SqlServerLineageLexer.IDENTIFIER)
                    .backquotedIdentifier(SqlServerLineageLexer.BACKQUOTED_IDENTIFIER)
                    .dot(SqlServerLineageLexer.DOT)
                    .comma(SqlServerLineageLexer.COMMA)
                    .semi(SqlServerLineageLexer.SEMI)
                    .lparen(SqlServerLineageLexer.LPAREN)
                    .rparen(SqlServerLineageLexer.RPAREN)
                    .star(SqlServerLineageLexer.STAR)
                    .eq(SqlServerLineageLexer.EQ)
                    .leadingProjectionToken(SqlServerLineageLexer.TOP)
                    .leadingProjectionKeyword("top");

    @Override
    public SqlDialect dialect() {
        return SqlDialect.SQLSERVER;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return SimpleTokenLineageParser.parse(tokens(sql), CONFIG);
    }

    private static List<Token> tokens(String sql) {
        SqlServerLineageLexer lexer = new SqlServerLineageLexer(CharStreams.fromString(sql));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<Token> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
