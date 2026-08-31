alter table mart.site_access
distributed by hash(user_id) default buckets 10
