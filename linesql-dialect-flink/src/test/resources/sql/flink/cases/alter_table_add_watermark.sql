alter table ods.orders
add watermark for event_time as event_time - interval '5' second;
