EXECUTE STATEMENT SET
BEGIN
    INSERT INTO mart.orders_daily PARTITION (region = 'EU') (order_id, amount)
    SELECT id, total FROM ods.orders WHERE ready;
    INSERT OVERWRITE mart.orders_audit PARTITION (audit_kind = 'daily') (order_id)
    SELECT id FROM ods.audit WHERE include_row;
END;
