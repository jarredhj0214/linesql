select
  decode(u.status, 'VIP', u.vip_score, u.base_score) as score,
  nvl2(u.nickname, u.nickname, u.name) as display_name
from app.users u;
