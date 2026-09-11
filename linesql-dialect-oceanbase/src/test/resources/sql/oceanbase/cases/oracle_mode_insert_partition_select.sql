insert into mart.user_summary partition (p202609)
  (user_id, user_name)
select id, name
from ods.users
where dt = date '2026-09-10';
