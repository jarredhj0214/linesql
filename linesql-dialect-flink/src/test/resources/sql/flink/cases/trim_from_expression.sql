SELECT
  TRIM(TRAILING ']' FROM raw_data) AS normalized_data
FROM ods.events
