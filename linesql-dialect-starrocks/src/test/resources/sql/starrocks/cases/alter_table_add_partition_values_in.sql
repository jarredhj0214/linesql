alter table mart.user_region
add partition p_us values in (('2024', 'us'), ('2024', 'ca'))
("replication_num" = "1")
