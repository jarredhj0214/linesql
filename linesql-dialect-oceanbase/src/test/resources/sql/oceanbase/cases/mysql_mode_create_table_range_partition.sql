create table mart.order_by_month (
  id bigint not null,
  created_at date,
  primary key (id)
)
engine = InnoDB
partition by range (created_at) (
  partition p202601 values less than ('2026-02-01'),
  partition pmax values less than maxvalue
)
