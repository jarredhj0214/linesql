select q.payload.user_id as user_id
from (
  select make_payload(e.user_id) as payload
  from app.events e
) q;
