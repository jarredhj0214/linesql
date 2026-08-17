SELECT t1.id, t2.id, t2.status
FROM (
  SELECT id
  FROM ods_users
  GROUP BY id
) t1
JOIN (
  SELECT id, status
  FROM dwd_orders
  GROUP BY id, status
) t2
ON t1.id = t2.id;
