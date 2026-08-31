create table mart.user_region (
  id bigint not null,
  region varchar(32)
)
partition by list columns(region) (
  partition p_cn values in ('CN', 'HK'),
  partition p_us values in ('US')
)
