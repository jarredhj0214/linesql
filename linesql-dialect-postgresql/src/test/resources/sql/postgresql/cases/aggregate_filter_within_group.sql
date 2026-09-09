SELECT o.user_id,
       count(*) FILTER (WHERE o.status = 'paid') AS paid_count,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY o.amount) AS median_amount
FROM sales.orders o
GROUP BY o.user_id
ORDER BY o.user_id NULLS LAST;
