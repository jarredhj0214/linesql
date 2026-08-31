alter table mart.site_access
split tablets partition (p20230106) (10001, 10002)
properties ("tablet_reshard_target_size" = "10GB")
