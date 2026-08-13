create or replace table mart.user_latest
using delta
as
select id as user_id, updated_at
from ods.users_delta
