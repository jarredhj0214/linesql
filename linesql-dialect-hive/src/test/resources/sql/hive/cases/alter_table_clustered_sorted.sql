alter table mart.orders
clustered by (customer_id)
sorted by (event_time desc, order_id asc)
into 32 buckets
