ALTER TABLE mart.site_access
REPLACE PARTITION (p1, p2) WITH TEMPORARY PARTITION (tp1, tp2)
PROPERTIES ("strict_range" = "true", "use_temp_partition_name" = "false");
