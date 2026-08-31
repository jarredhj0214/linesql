alter table mart.site_access
add partition p20230106 values [('2023-01-06'), ('2023-01-07'))
distributed by hash(site_id, city_code) buckets 30
