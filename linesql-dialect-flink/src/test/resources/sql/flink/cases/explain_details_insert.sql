EXPLAIN ESTIMATED_COST, CHANGELOG_MODE
INSERT INTO ads.word_counts(word, cnt)
SELECT word, COUNT(word) AS cnt
FROM dwd.words
GROUP BY word
