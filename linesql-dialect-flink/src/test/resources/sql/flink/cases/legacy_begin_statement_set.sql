BEGIN STATEMENT SET;
INSERT INTO mart.order_events (id) SELECT id FROM ods.orders;
INSERT OVERWRITE mart.order_snapshot (id) SELECT id FROM ods.orders;
END;
