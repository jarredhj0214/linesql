SELECT
  round(cnt / (SELECT count(*) FROM ods.events WHERE ev_soc > 0), 2) AS ratio
FROM (
  SELECT count(*) AS cnt
  FROM ods.events
) s
