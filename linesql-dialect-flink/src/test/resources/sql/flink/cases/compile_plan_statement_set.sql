COMPILE PLAN IF NOT EXISTS 'file:///tmp/orders.json' FOR EXECUTE STATEMENT SET
BEGIN
    INSERT INTO mart.order_events (id) SELECT id FROM ods.orders;
    INSERT INTO mart.order_audit (id) SELECT order_id FROM ods.audit;
END;
