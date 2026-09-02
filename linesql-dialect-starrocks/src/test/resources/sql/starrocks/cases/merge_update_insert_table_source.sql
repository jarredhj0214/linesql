merge into ads.user_dim t
using ods.user_delta s
on t.user_id = s.user_id
when matched and s.op = 'U' then update set name = s.name, updated_at = s.event_time
when not matched and s.op = 'I' then insert (user_id, name, updated_at) values (s.user_id, s.name, s.event_time);
