create temporary table mart.orders_copy
partition by date_trunc('day', dt)
distributed by hash(dt)
properties ("replication_num" = "1")
like ods.orders
