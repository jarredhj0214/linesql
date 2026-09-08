SELECT id, user_id
FROM mart.orders
WHERE dt = current_date
FOR SHARE SKIP LOCKED;
