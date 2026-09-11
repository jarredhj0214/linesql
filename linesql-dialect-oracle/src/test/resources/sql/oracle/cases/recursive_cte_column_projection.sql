with org_tree (id, parent_id) as (
  select id, parent_id
  from app.org
  where parent_id is null
  union all
  select c.id, c.parent_id
  from app.org c
  join org_tree p on c.parent_id = p.id
)
select id, parent_id
from org_tree;
