create or replace temporary view tmp_users as
select id as user_id, name
from ods.users;

insert into table ads.user_summary (user_id, user_name)
select user_id, name
from tmp_users
