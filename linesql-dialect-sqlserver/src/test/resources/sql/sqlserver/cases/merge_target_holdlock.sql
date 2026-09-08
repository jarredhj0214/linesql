merge into ads.user_summary with (holdlock) as t
using ods.users_delta as s
on t.user_id = s.id
when matched then update set user_name = s.name
when not matched by target then insert (user_id, user_name)
                                values (s.id, s.name);
