SELECT
  POSITION('error' IN reason_text) AS error_pos
FROM ods.events
