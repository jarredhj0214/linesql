SELECT
  JSON_OBJECT(KEY user_id VALUE amount ABSENT ON NULL) AS payload
FROM dwd.orders
