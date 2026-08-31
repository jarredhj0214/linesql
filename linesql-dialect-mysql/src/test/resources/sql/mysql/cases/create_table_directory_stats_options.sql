create table mart.audit_log (
  id bigint primary key,
  event_time datetime
)
engine = InnoDB
data directory = '/data/mysql/audit'
index directory = '/index/mysql/audit'
stats_persistent = 1
stats_auto_recalc = default
stats_sample_pages = 32;
