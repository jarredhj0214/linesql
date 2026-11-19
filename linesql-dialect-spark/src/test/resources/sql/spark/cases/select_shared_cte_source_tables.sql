WITH base AS (
  SELECT a.id, a.amount, a.quantity, b.adjustment
  FROM sample.events a JOIN sample.adjustments b ON a.id = b.id
), totals AS (
  SELECT id, amount + adjustment AS net, quantity, amount AS original
  FROM base
), summary AS (
  SELECT id, net * quantity AS total, original FROM totals
)
SELECT id, total, original FROM summary
