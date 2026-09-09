select
  iif(u.status = 'VIP', u.vip_score, u.base_score) as score,
  choose(u.level_no, u.level1_name, u.level2_name, u.level3_name) as level_name
from dbo.users u;
