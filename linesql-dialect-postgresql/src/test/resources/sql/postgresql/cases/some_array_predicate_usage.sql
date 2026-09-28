select id
from app.document_acl
where denied_actor_id <> some(blocked_actor_ids);
