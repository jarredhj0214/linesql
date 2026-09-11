merge into mart.users t
using staging.users_delta s
on t.id = s.id
when matched and s.op = 'D' then delete
when matched and s.op = 'I' then do nothing
when not matched and s.op = 'S' then do nothing;
