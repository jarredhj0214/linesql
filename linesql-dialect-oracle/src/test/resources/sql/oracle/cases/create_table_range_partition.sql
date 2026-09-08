create table mart.order_part (
  id number,
  dt varchar2(10)
)
partition by range (dt) (
  partition p202609 values less than ('2026-10-01'),
  partition pmax values less than (maxvalue)
)
