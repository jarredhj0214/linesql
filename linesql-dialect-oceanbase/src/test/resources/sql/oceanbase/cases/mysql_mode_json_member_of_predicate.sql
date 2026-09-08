select id
from app.user_segments
where user_id member of(payload->'$.eligibleUserIds');
