create materialized view ads.mv_user_activity
properties ("bloom_filter_columns" = "user_id,event_id")
as
select user_id, event_id
from dwd.user_activity;
