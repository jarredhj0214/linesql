DELETE o
FROM audit.removal_log z, mart.orders o JOIN staging.removals r USING (order_id)
WHERE r.expired = 1 AND z.id = r.log_id;
