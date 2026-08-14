package io.github.linesql.dialect.oracle;

import io.github.linesql.core.internal.SimpleTokenLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.oracle.antlr.OracleLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class OracleDialectParser implements DialectParser {
    private static final SimpleTokenLineageParser.Config CONFIG =
            SimpleTokenLineageParser.Config.forDialect(SqlDialect.ORACLE, "Oracle", "ORACLE")
                    .select(OracleLineageLexer.SELECT)
                    .insert(OracleLineageLexer.INSERT)
                    .create(OracleLineageLexer.CREATE)
                    .overwrite(OracleLineageLexer.OVERWRITE)
                    .into(OracleLineageLexer.INTO)
                    .external(OracleLineageLexer.EXTERNAL)
                    .temporary(OracleLineageLexer.TEMPORARY)
                    .table(OracleLineageLexer.TABLE)
                    .view(OracleLineageLexer.VIEW)
                    .ifToken(OracleLineageLexer.IF)
                    .not(OracleLineageLexer.NOT)
                    .exists(OracleLineageLexer.EXISTS)
                    .as(OracleLineageLexer.AS)
                    .from(OracleLineageLexer.FROM)
                    .join(OracleLineageLexer.JOIN)
                    .inner(OracleLineageLexer.INNER)
                    .left(OracleLineageLexer.LEFT)
                    .right(OracleLineageLexer.RIGHT)
                    .full(OracleLineageLexer.FULL)
                    .cross(OracleLineageLexer.CROSS)
                    .outer(OracleLineageLexer.OUTER)
                    .on(OracleLineageLexer.ON)
                    .where(OracleLineageLexer.WHERE)
                    .group(OracleLineageLexer.GROUP)
                    .having(OracleLineageLexer.HAVING)
                    .order(OracleLineageLexer.ORDER)
                    .limit(OracleLineageLexer.LIMIT)
                    .union(OracleLineageLexer.UNION)
                    .partition(OracleLineageLexer.PARTITION)
                    .stored(OracleLineageLexer.STORED)
                    .row(OracleLineageLexer.ROW)
                    .identifier(OracleLineageLexer.IDENTIFIER)
                    .backquotedIdentifier(OracleLineageLexer.BACKQUOTED_IDENTIFIER)
                    .dot(OracleLineageLexer.DOT)
                    .comma(OracleLineageLexer.COMMA)
                    .semi(OracleLineageLexer.SEMI)
                    .lparen(OracleLineageLexer.LPAREN)
                    .rparen(OracleLineageLexer.RPAREN)
                    .star(OracleLineageLexer.STAR);

    @Override
    public SqlDialect dialect() {
        return SqlDialect.ORACLE;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return SimpleTokenLineageParser.parse(tokens(sql), CONFIG);
    }

    private static List<Token> tokens(String sql) {
        OracleLineageLexer lexer = new OracleLineageLexer(CharStreams.fromString(sql));
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
