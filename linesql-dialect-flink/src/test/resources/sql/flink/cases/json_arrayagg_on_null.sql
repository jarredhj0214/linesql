SELECT
  region,
  JSON_ARRAYAGG(amount ABSENT ON NULL) AS amounts
FROM dwd.orders
GROUP BY region
