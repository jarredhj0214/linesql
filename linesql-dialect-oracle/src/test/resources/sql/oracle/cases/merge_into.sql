merge into ads.user_summary t
using ods.users s
on (t.user_id = s.id)
when matched then update set user_name = upper(s.name),
                             order_score = s.order_count + t.bonus_count
when not matched then insert (user_id, user_name, order_score)
                      values (s.id, s.name, s.order_count)
