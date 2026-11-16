package io.github.linesql.core.util;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.atn.LexerATNSimulator;

public final class AntlrLexerUtils {
    private AntlrLexerUtils() {
    }

    /**
     * Consumes a nested block comment after the lexer has matched its opening delimiter.
     */
    public static void consumeNestedBlockComment(Lexer lexer) {
        CharStream input = lexer.getInputStream();
        LexerATNSimulator interpreter = lexer.getInterpreter();
        int start = input.index() - 2;
        int depth = 1;
        while (input.LA(1) != IntStream.EOF) {
            int first = input.LA(1);
            int second = input.LA(2);
            interpreter.consume(input);
            if (first == '/' && second == '*') {
                depth++;
                interpreter.consume(input);
            } else if (first == '*' && second == '/') {
                interpreter.consume(input);
                if (--depth == 0) {
                    return;
                }
            }
        }
        lexer.notifyListeners(new LexerNoViableAltException(lexer, input, start, null));
    }
}
