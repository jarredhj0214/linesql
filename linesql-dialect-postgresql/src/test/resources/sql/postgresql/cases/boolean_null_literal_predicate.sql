select id
from public.users
where active = true
  and deleted_at is null
  and verified is not false;
