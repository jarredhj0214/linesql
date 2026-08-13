package io.github.linesql.dialect.spark;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.misc.Interval;

import java.util.Locale;

final class UpperCaseCharStream implements CharStream {
    private final CharStream delegate;

    UpperCaseCharStream(CharStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getText(Interval interval) {
        return delegate.getText(interval);
    }

    @Override
    public void consume() {
        delegate.consume();
    }

    @Override
    public int LA(int i) {
        int value = delegate.LA(i);
        if (value <= 0) {
            return value;
        }
        return String.valueOf((char) value).toUpperCase(Locale.ROOT).charAt(0);
    }

    @Override
    public int mark() {
        return delegate.mark();
    }

    @Override
    public void release(int marker) {
        delegate.release(marker);
    }

    @Override
    public int index() {
        return delegate.index();
    }

    @Override
    public void seek(int index) {
        delegate.seek(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public String getSourceName() {
        return delegate.getSourceName();
    }
}
