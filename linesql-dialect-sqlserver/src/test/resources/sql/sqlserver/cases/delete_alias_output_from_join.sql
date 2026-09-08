delete u
output deleted.id, deleted.name
into audit.deleted_users (id, name)
from dbo.users u
join dbo.user_blacklist b on u.id = b.user_id
where b.reason = 'expired';
