merge into ads.user_summary as t
using ods.users_delta as s
on t.user_id = s.id
when matched then update set user_name = s.name
when not matched then insert (user_id, user_name) values (s.id, s.name)
output $action, inserted.user_id, deleted.user_name
into audit.user_summary_merge_actions (merge_action, user_id, old_user_name);
