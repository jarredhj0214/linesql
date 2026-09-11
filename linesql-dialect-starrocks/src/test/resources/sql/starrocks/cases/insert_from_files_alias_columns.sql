insert into ods.user_events (user_id, event_name)
select f.user_id, f.event_name
from files(
  "path" = "s3://bucket/events/*.parquet",
  "format" = "parquet"
) as f(user_id, event_name, dt)
where f.dt = '2026-09-10'
order by f.user_id;
