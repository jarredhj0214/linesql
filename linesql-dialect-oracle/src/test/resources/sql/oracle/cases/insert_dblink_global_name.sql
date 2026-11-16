INSERT INTO ods.remote_orders@warehouse.example.com (order_id)
SELECT order_id FROM staging.pending_orders WHERE ready = 1;
