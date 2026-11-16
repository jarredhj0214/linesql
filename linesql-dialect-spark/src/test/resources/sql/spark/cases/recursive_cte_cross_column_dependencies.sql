WITH RECURSIVE walk AS (
    SELECT n.id AS a, n.parent AS b, n.root AS c FROM org.nodes n
    UNION ALL
    SELECT w.b, w.c, w.a FROM walk w WHERE w.a > 0
)
SELECT a, b, c FROM walk;
