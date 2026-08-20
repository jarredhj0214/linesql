SELECT
  score
FROM (SELECT * FROM ods.events) e
LEFT JOIN (
  SELECT vin AS vin_r
  FROM dim.vehicles
) v ON e.vin = v.vin_r
