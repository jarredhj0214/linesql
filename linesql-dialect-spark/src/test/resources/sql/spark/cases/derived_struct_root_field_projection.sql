SELECT
  values.vin,
  values.timestamp AS ts
FROM (
  SELECT
    from_json(content, 'vin STRING, timestamp BIGINT') AS values
  FROM ods.events
) s
