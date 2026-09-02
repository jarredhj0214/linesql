SELECT
  region,
  JSON_OBJECTAGG(KEY user_id VALUE amount NULL ON NULL) AS amount_by_user
FROM dwd.orders
GROUP BY region
