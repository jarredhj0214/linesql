package io.github.linesql.dialect.starrocks;

import io.github.linesql.core.internal.SimpleTokenLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.starrocks.antlr.StarRocksLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class StarRocksDialectParser implements DialectParser {
    private static final SimpleTokenLineageParser.Config CONFIG =
            SimpleTokenLineageParser.Config.forDialect(SqlDialect.STARROCKS, "StarRocks", "STARROCKS")
                    .select(StarRocksLineageLexer.SELECT)
                    .insert(StarRocksLineageLexer.INSERT)
                    .update(StarRocksLineageLexer.UPDATE)
                    .delete(StarRocksLineageLexer.DELETE)
                    .create(StarRocksLineageLexer.CREATE)
                    .overwrite(StarRocksLineageLexer.OVERWRITE)
                    .into(StarRocksLineageLexer.INTO)
                    .external(StarRocksLineageLexer.EXTERNAL)
                    .temporary(StarRocksLineageLexer.TEMPORARY)
                    .table(StarRocksLineageLexer.TABLE)
                    .view(StarRocksLineageLexer.VIEW)
                    .ifToken(StarRocksLineageLexer.IF)
                    .not(StarRocksLineageLexer.NOT)
                    .exists(StarRocksLineageLexer.EXISTS)
                    .as(StarRocksLineageLexer.AS)
                    .set(StarRocksLineageLexer.SET)
                    .with(StarRocksLineageLexer.WITH)
                    .from(StarRocksLineageLexer.FROM)
                    .using(StarRocksLineageLexer.USING)
                    .join(StarRocksLineageLexer.JOIN)
                    .inner(StarRocksLineageLexer.INNER)
                    .left(StarRocksLineageLexer.LEFT)
                    .right(StarRocksLineageLexer.RIGHT)
                    .full(StarRocksLineageLexer.FULL)
                    .cross(StarRocksLineageLexer.CROSS)
                    .outer(StarRocksLineageLexer.OUTER)
                    .on(StarRocksLineageLexer.ON)
                    .where(StarRocksLineageLexer.WHERE)
                    .group(StarRocksLineageLexer.GROUP)
                    .having(StarRocksLineageLexer.HAVING)
                    .order(StarRocksLineageLexer.ORDER)
                    .limit(StarRocksLineageLexer.LIMIT)
                    .union(StarRocksLineageLexer.UNION)
                    .partition(StarRocksLineageLexer.PARTITION)
                    .stored(StarRocksLineageLexer.STORED)
                    .row(StarRocksLineageLexer.ROW)
                    .identifier(StarRocksLineageLexer.IDENTIFIER)
                    .backquotedIdentifier(StarRocksLineageLexer.BACKQUOTED_IDENTIFIER)
                    .dot(StarRocksLineageLexer.DOT)
                    .comma(StarRocksLineageLexer.COMMA)
                    .semi(StarRocksLineageLexer.SEMI)
                    .lparen(StarRocksLineageLexer.LPAREN)
                    .rparen(StarRocksLineageLexer.RPAREN)
                    .star(StarRocksLineageLexer.STAR)
                    .eq(StarRocksLineageLexer.EQ);

    @Override
    public SqlDialect dialect() {
        return SqlDialect.STARROCKS;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return SimpleTokenLineageParser.parse(tokens(sql), CONFIG);
    }

    private static List<Token> tokens(String sql) {
        StarRocksLineageLexer lexer = new StarRocksLineageLexer(CharStreams.fromString(sql));
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
