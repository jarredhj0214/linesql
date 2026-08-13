create temporary view tmp_users as
select id
from ods.users;

drop view tmp_users;

select id
from tmp_users
