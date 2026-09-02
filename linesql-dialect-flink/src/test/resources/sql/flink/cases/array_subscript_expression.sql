SELECT
  data[1].id AS id,
  signals[1] AS first_signal
FROM ods.events
WHERE signals[1] IS NOT NULL
