WITH daily_amount AS (
  SELECT amount
  FROM ods.orders
)
SELECT (
  SELECT max(amount)
  FROM daily_amount
) AS max_amount
