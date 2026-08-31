alter table iceberg.sales.orders create or replace tag if not exists `daily-20260828` as of version 123456 retain 7 days
