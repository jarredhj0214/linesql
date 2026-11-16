UPDATE mart.orders o
SET o.status = s.next_status
FROM staging.status_updates s
JOIN staging.active_regions r ON s.region_id = r.id
WHERE o.id = s.order_id AND r.active = 1;
