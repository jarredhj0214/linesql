alter table mart.orders
modify partition (p202608, p202609)
set ("replication_num" = "3")
