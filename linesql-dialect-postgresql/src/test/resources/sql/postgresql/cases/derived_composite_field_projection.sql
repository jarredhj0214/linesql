select q.payload.user_id as user_id
from (
  select jsonb_build_object('user_id', e.user_id) as payload
  from app.events e
) q;
