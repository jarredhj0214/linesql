SELECT o.user_id,
       sum(o.amount) OVER (
         PARTITION BY o.user_id
         ORDER BY o.created_at
         ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
       ) AS rolling_amount
FROM sales.orders o;
