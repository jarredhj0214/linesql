package io.github.linesql.dialect.flink;

import io.github.linesql.core.internal.SimpleTokenLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.flink.antlr.FlinkLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class FlinkDialectParser implements DialectParser {
    private static final SimpleTokenLineageParser.Config CONFIG =
            SimpleTokenLineageParser.Config.forDialect(SqlDialect.FLINK, "Flink", "FLINK")
                    .select(FlinkLineageLexer.SELECT)
                    .insert(FlinkLineageLexer.INSERT)
                    .update(FlinkLineageLexer.UPDATE)
                    .delete(FlinkLineageLexer.DELETE)
                    .create(FlinkLineageLexer.CREATE)
                    .overwrite(FlinkLineageLexer.OVERWRITE)
                    .into(FlinkLineageLexer.INTO)
                    .external(FlinkLineageLexer.EXTERNAL)
                    .temporary(FlinkLineageLexer.TEMPORARY)
                    .table(FlinkLineageLexer.TABLE)
                    .view(FlinkLineageLexer.VIEW)
                    .ifToken(FlinkLineageLexer.IF)
                    .not(FlinkLineageLexer.NOT)
                    .exists(FlinkLineageLexer.EXISTS)
                    .as(FlinkLineageLexer.AS)
                    .set(FlinkLineageLexer.SET)
                    .from(FlinkLineageLexer.FROM)
                    .using(FlinkLineageLexer.USING)
                    .join(FlinkLineageLexer.JOIN)
                    .inner(FlinkLineageLexer.INNER)
                    .left(FlinkLineageLexer.LEFT)
                    .right(FlinkLineageLexer.RIGHT)
                    .full(FlinkLineageLexer.FULL)
                    .cross(FlinkLineageLexer.CROSS)
                    .outer(FlinkLineageLexer.OUTER)
                    .on(FlinkLineageLexer.ON)
                    .where(FlinkLineageLexer.WHERE)
                    .group(FlinkLineageLexer.GROUP)
                    .having(FlinkLineageLexer.HAVING)
                    .order(FlinkLineageLexer.ORDER)
                    .limit(FlinkLineageLexer.LIMIT)
                    .union(FlinkLineageLexer.UNION)
                    .partition(FlinkLineageLexer.PARTITION)
                    .stored(FlinkLineageLexer.STORED)
                    .row(FlinkLineageLexer.ROW)
                    .identifier(FlinkLineageLexer.IDENTIFIER)
                    .backquotedIdentifier(FlinkLineageLexer.BACKQUOTED_IDENTIFIER)
                    .dot(FlinkLineageLexer.DOT)
                    .comma(FlinkLineageLexer.COMMA)
                    .semi(FlinkLineageLexer.SEMI)
                    .lparen(FlinkLineageLexer.LPAREN)
                    .rparen(FlinkLineageLexer.RPAREN)
                    .star(FlinkLineageLexer.STAR)
                    .eq(FlinkLineageLexer.EQ);

    @Override
    public SqlDialect dialect() {
        return SqlDialect.FLINK;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return SimpleTokenLineageParser.parse(tokens(sql), CONFIG);
    }

    private static List<Token> tokens(String sql) {
        FlinkLineageLexer lexer = new FlinkLineageLexer(CharStreams.fromString(sql));
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
