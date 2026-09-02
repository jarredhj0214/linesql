SELECT
  JSON_ARRAY(user_id, amount NULL ON NULL) AS payload
FROM dwd.orders
