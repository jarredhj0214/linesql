merge into ads.users t
using ods.user_updates s
on t.id = s.id
when matched then update set name = upper(s.name), score = s.score + t.bonus_score
when not matched then insert (id, name, score) values (s.id, s.name, s.score)
