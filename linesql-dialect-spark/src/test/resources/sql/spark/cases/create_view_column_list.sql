create view mart.v_users_with_columns (user_id comment 'id', user_name) as
select id, name
from ods.users
