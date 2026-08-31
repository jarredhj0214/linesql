alter table app.orders reorganize partition p202608 into (
  partition p202608a values less than ('2026-08-15'),
  partition p202608b values less than ('2026-09-01')
);
