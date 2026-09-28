merge into ads.user_summary as t
using ods.users_delta as s
on t.user_id = s.id
when matched then update set user_name = s.name
when not matched then insert (user_id, user_name) values (s.id, s.name)
output $action as merge_action,
       inserted.user_id as user_id,
       deleted.user_name as old_user_name;
