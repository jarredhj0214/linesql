alter view ads.v_user_columns (uid, uname) as
select id, name
from ods.users
where status = 'ACTIVE';
