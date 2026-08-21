with recursive user_tree as (
  select id, parent_id, name
  from app.users
  where parent_id is null
  union all
  select u.id, u.parent_id, u.name
  from app.users u
  join user_tree t on u.parent_id = t.id
)
select id, name
from user_tree;
