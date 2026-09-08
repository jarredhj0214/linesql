alter table mart.order_part reorganize partition p202609 into (
  partition p202609a values less than ('2026-09-15'),
  partition p202609b values less than ('2026-10-01')
);
