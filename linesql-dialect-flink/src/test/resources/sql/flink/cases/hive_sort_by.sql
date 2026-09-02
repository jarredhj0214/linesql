SELECT x, y
FROM hive_src
SORT BY abs(y) DESC
