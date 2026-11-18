EXPORT TABLE dwd.orders PARTITION (dt='2026-11-18')
TO '/archive/orders' FOR REPLICATION('42');
