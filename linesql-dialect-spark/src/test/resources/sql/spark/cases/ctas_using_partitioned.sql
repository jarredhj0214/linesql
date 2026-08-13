create table mart.user_events
using parquet
partitioned by (dt)
as
select id, user_id
from ods.events
