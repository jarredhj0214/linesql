COMPILE AND EXECUTE PLAN 'file:///tmp/orders;daily.json' FOR STATEMENT SET
BEGIN
    INSERT INTO mart.order_events (id)
    SELECT id FROM ods.orders WHERE paid;
    INSERT INTO mart.order_audit (id)
    SELECT order_id FROM ods.audit WHERE visible;
END;
