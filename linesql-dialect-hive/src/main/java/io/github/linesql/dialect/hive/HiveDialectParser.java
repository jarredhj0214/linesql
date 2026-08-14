package io.github.linesql.dialect.hive;

import io.github.linesql.core.internal.SimpleTokenLineageParser;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.ParseContext;
import io.github.linesql.core.model.ParseOptions;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.spi.DialectParser;
import io.github.linesql.dialect.hive.antlr.HiveLineageLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class HiveDialectParser implements DialectParser {
    private static final SimpleTokenLineageParser.Config CONFIG =
            SimpleTokenLineageParser.Config.forDialect(SqlDialect.HIVE, "Hive", "HIVE")
                    .select(HiveLineageLexer.SELECT)
                    .insert(HiveLineageLexer.INSERT)
                    .update(HiveLineageLexer.UPDATE)
                    .delete(HiveLineageLexer.DELETE)
                    .create(HiveLineageLexer.CREATE)
                    .drop(HiveLineageLexer.DROP)
                    .truncate(HiveLineageLexer.TRUNCATE)
                    .alter(HiveLineageLexer.ALTER)
                    .show(HiveLineageLexer.SHOW)
                    .describe(HiveLineageLexer.DESCRIBE)
                    .comment(HiveLineageLexer.COMMENT)
                    .overwrite(HiveLineageLexer.OVERWRITE)
                    .into(HiveLineageLexer.INTO)
                    .external(HiveLineageLexer.EXTERNAL)
                    .temporary(HiveLineageLexer.TEMPORARY)
                    .table(HiveLineageLexer.TABLE)
                    .view(HiveLineageLexer.VIEW)
                    .ifToken(HiveLineageLexer.IF)
                    .not(HiveLineageLexer.NOT)
                    .exists(HiveLineageLexer.EXISTS)
                    .as(HiveLineageLexer.AS)
                    .rename(HiveLineageLexer.RENAME)
                    .to(HiveLineageLexer.TO)
                    .column(HiveLineageLexer.COLUMN)
                    .set(HiveLineageLexer.SET)
                    .with(HiveLineageLexer.WITH)
                    .from(HiveLineageLexer.FROM)
                    .using(HiveLineageLexer.USING)
                    .join(HiveLineageLexer.JOIN)
                    .inner(HiveLineageLexer.INNER)
                    .left(HiveLineageLexer.LEFT)
                    .right(HiveLineageLexer.RIGHT)
                    .full(HiveLineageLexer.FULL)
                    .cross(HiveLineageLexer.CROSS)
                    .outer(HiveLineageLexer.OUTER)
                    .on(HiveLineageLexer.ON)
                    .where(HiveLineageLexer.WHERE)
                    .group(HiveLineageLexer.GROUP)
                    .having(HiveLineageLexer.HAVING)
                    .order(HiveLineageLexer.ORDER)
                    .limit(HiveLineageLexer.LIMIT)
                    .union(HiveLineageLexer.UNION)
                    .partition(HiveLineageLexer.PARTITION)
                    .stored(HiveLineageLexer.STORED)
                    .row(HiveLineageLexer.ROW)
                    .identifier(HiveLineageLexer.IDENTIFIER)
                    .backquotedIdentifier(HiveLineageLexer.BACKQUOTED_IDENTIFIER)
                    .dot(HiveLineageLexer.DOT)
                    .comma(HiveLineageLexer.COMMA)
                    .semi(HiveLineageLexer.SEMI)
                    .lparen(HiveLineageLexer.LPAREN)
                    .rparen(HiveLineageLexer.RPAREN)
                    .star(HiveLineageLexer.STAR)
                    .eq(HiveLineageLexer.EQ);

    @Override
    public SqlDialect dialect() {
        return SqlDialect.HIVE;
    }

    @Override
    public LineageResult parse(String sql, ParseOptions options, ParseContext context) {
        return SimpleTokenLineageParser.parse(tokens(sql), CONFIG);
    }

    private static List<Token> tokens(String sql) {
        HiveLineageLexer lexer = new HiveLineageLexer(CharStreams.fromString(sql));
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
