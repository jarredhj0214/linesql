create temporary table tmp.session_events (
  session_id bigint,
  user_id bigint,
  event_time datetime
)
engine = olap
duplicate key(session_id)
distributed by hash(session_id) buckets 8
properties (
  "replication_num" = "1"
);
