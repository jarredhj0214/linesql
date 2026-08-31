alter table mart.site_access add index idx_city (city) using bitmap comment 'city bitmap index'
