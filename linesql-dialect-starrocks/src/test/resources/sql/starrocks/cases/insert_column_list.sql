insert into ads.user_summary (uid, uname)
select id, name
from ods.users;
