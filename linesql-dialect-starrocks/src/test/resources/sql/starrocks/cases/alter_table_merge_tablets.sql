alter table mart.site_access
merge tablets (10001, 10002, 10003)
properties ("tablet_reshard_target_size" = "20GB")
