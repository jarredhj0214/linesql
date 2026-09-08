create table mart.order_list_columns (
  id bigint,
  region varchar(16),
  dt date
)
partition by list columns(region) (
  partition p_cn values in ('CN'),
  partition p_us values in ('US')
)
