EXPLAIN PLAN FOR
SELECT `count`, COUNT(word) AS word_count
FROM (
  SELECT `count`, word FROM MyTable1 WHERE word LIKE 'F%'
  UNION ALL
  SELECT `count`, word FROM MyTable2
) tmp
GROUP BY `count`
