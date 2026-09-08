select a.id, b.name
from ods.users a, ods.user_profile b
where a.id = b.user_id(+);
