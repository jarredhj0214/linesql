select case when u.status = 'A' then u.score else 0 end as active_score
from ods.users u;
