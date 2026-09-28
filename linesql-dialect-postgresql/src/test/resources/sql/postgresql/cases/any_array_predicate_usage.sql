select id
from app.document_acl
where actor_id = any(allowed_actor_ids);
