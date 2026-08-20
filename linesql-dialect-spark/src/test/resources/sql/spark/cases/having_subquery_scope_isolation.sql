SELECT
  vin,
  min(dt) AS min_dt
FROM (
  SELECT vin, dt FROM ods.events
  UNION ALL
  SELECT vin, dt FROM dwd.events
) s
GROUP BY vin
HAVING vin IN (SELECT vin FROM dim.vehicles)
