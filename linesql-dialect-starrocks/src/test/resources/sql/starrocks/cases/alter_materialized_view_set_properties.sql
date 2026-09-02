alter materialized view mart.mv_user_orders
set ("session.insert_timeout" = "3600", "bloom_filter_columns" = "user_id, order_id");
