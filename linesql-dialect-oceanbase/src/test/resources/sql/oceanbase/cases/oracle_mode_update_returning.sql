UPDATE ods.orders
SET status = 'DONE'
WHERE id = 1
RETURNING status INTO v_status;
