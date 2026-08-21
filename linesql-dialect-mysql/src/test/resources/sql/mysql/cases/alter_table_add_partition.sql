alter table app.users add partition (
  partition p202608 values less than (202609)
);
