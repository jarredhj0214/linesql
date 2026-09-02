merge into ads.user_dim t
using ods.user_delta s
on t.user_id = s.user_id
when matched and s.op = 'D' then delete;
