select payload->>'$.user.id' as user_id,
       payload->'$.tags' as tags
from app.events
where payload->>'$.type' = 'click';
