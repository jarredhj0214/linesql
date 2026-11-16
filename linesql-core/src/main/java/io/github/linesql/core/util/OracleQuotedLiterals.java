package io.github.linesql.core.util;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.LexerNoViableAltException;

public final class OracleQuotedLiterals {
    private OracleQuotedLiterals() {
    }

    /** Returns the end of an alternative-quoted literal, or -1 if no literal starts here. */
    public static int endOfLiteral(String text, int start) {
        char first = text.charAt(start);
        int q = first == 'n' || first == 'N' ? start + 1 : start;
        if (q + 2 >= text.length() || (text.charAt(q) != 'q' && text.charAt(q) != 'Q')
                || text.charAt(q + 1) != '\'') {
            return -1;
        }
        if (start > 0) {
            int previous = text.codePointBefore(start);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == '$' || previous == '#') {
                return -1;
            }
        }
        int opening = text.codePointAt(q + 2);
        if (opening == ' ' || opening == '\t' || opening == '\r' || opening == '\n') {
            return -1;
        }
        String closing = new String(Character.toChars(closingDelimiter(opening))) + "'";
        int end = text.indexOf(closing, q + 2 + Character.charCount(opening));
        return end < 0 ? text.length() : end + closing.length();
    }

    /** Consumes the body after the lexer has matched Q or NQ and the opening delimiter. */
    public static void consumeBody(Lexer lexer) {
        CharStream input = lexer.getInputStream();
        int closing = closingDelimiter(input.LA(-1));
        while (input.LA(1) != IntStream.EOF) {
            if (input.LA(1) == closing && input.LA(2) == '\'') {
                lexer.getInterpreter().consume(input);
                lexer.getInterpreter().consume(input);
                return;
            }
            lexer.getInterpreter().consume(input);
        }
        lexer.notifyListeners(new LexerNoViableAltException(lexer, input, lexer._tokenStartCharIndex, null));
    }

    private static int closingDelimiter(int opening) {
        switch (opening) {
            case '[':
                return ']';
            case '{':
                return '}';
            case '(':
                return ')';
            case '<':
                return '>';
            default:
                return opening;
        }
    }
}
