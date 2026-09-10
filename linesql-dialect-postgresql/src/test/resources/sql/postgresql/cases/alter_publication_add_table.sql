alter publication pub_sales
add table mart.payments
with (publish_via_partition_root = true);
