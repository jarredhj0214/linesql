CREATE PROCEDURE IF NOT EXISTS mart.refresh_audit(IN p_id BIGINT, OUT p_count BIGINT)
MODIFIES SQL DATA SQL SECURITY INVOKER
BEGIN
    INSERT INTO mart.audit (order_id, net_amount)
    SELECT o.id, o.amount - o.discount
    FROM ods.orders o
    WHERE o.active = 1 AND o.id >= p_id;
    DELETE FROM mart.audit WHERE net_amount < 0;
END;
