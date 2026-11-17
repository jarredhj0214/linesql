USE unrelated;
ALTER TABLE ods.orders RENAME orders_archived;
ALTER TABLE ods.live_orders SWAP WITH incoming_orders;
ALTER MATERIALIZED VIEW mart.sales_mv RENAME sales_daily;
ALTER MATERIALIZED VIEW mart.active_mv SWAP WITH staged_mv;
