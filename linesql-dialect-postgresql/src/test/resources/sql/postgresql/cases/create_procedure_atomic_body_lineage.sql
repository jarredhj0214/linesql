CREATE PROCEDURE mart.refresh_audit()
LANGUAGE SQL
BEGIN ATOMIC
    INSERT INTO mart.audit (order_id, amount)
    SELECT src.id, src.amount FROM ods.orders src WHERE src.active;
    UPDATE mart.audit a SET amount = src.amount
    FROM ods.adjustments src WHERE src.order_id = a.order_id;
END;
