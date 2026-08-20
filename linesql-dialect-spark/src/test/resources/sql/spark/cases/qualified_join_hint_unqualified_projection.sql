SELECT
  count(CASE WHEN kind_id = 6 THEN 1 END) AS kind_cnt
FROM ods.logs l
JOIN dim.kind d ON l.kind_id = d.id
