SELECT t1.id, t2.id, t2.status
FROM (
  SELECT id
  FROM app.users
  GROUP BY id
) t1
JOIN (
  SELECT id, status
  FROM app.orders
  GROUP BY id, status
) t2
ON t1.id = t2.id;
