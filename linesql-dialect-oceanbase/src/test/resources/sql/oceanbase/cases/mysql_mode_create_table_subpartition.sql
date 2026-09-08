create table mart.order_subpart (
  id bigint not null,
  created_at date,
  user_id bigint
)
partition by range columns(created_at)
subpartition by hash(user_id) subpartitions 4 (
  partition p202601 values less than ('2026-02-01'),
  partition pmax values less than maxvalue
)
