CREATE UNIQUE INDEX idx_orders_biz ON mart.orders (biz_id) GLOBAL PARTITION BY (biz_id) PARTITIONS 8;
