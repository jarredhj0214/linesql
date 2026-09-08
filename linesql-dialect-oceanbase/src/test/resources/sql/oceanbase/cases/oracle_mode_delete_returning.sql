DELETE FROM ods.orders
WHERE status = 'EXPIRED'
RETURNING id INTO v_order_id;
