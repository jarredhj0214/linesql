select case
    when u.status = 'A' then u.score
    when u.status = 'P' then u.pending_score
    else u.default_score
  end as normalized_score
from ods_users u;
