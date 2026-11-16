SELECT o.id FROM ods.orders o
WHERE o.state = 'ACTIVE'
QUALIFY ROW_NUMBER() OVER (PARTITION BY o.customer_id ORDER BY o.created_at DESC) = 1
ORDER BY o.id FETCH FIRST 5 ROWS ONLY;
