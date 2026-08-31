alter table mart.order_metrics drop partitions start ("2026-06-01") end ("2026-09-01") every (interval 1 month)
