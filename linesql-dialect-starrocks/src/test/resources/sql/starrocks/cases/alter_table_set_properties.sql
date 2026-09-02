alter table mart.orders set ("dynamic_partition.enable" = "true", "bloom_filter_columns" = "user_id,order_id");
