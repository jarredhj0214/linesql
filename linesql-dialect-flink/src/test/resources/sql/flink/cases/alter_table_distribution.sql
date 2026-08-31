alter table ods.orders add distribution by hash(order_id) into 4 buckets;
