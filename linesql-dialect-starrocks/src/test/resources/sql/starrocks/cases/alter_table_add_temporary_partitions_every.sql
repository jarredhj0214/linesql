alter table mart.site_access
add temporary partitions start ('2024-01-01') end ('2024-02-01') every (interval 1 day)
distributed by random buckets 8
