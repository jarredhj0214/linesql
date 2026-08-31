alter table mart.site_access
drop partitions where dt < current_date() - interval 3 month
