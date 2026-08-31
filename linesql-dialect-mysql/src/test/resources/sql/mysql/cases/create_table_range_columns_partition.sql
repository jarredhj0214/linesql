create table mart.order_by_day (
  id bigint not null,
  created_at date,
  primary key (id, created_at)
)
engine = InnoDB
partition by range columns(created_at) (
  partition p202601 values less than ('2026-02-01'),
  partition pmax values less than maxvalue
)
