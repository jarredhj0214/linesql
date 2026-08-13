package io.github.linesql.core.spi;

import java.util.List;

public interface StatementSplitter {
    List<String> split(String script);
}
