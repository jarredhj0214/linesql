SELECT u.id,
       CASE
           WHEN EXISTS (
               SELECT 1
               FROM app.orders o
               WHERE o.user_id = u.id AND o.status = 'PAID'
           )
           THEN u.vip_score
           ELSE u.base_score
       END AS risk_score
FROM app.users u
WHERE u.status = 'ACTIVE';
