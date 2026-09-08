INSERT INTO ods.orders (id, status)
VALUES (1, 'NEW')
RETURNING id INTO v_order_id;
