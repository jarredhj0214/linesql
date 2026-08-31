select a.id,
       a.type as account_type,
       a.group as account_group
from app.accounts a
where a.type = 'SERVICE'
  and a.group <> 'disabled'
