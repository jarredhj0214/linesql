select id as org_id, parent_id
from app.org_units
start with parent_id is null
connect by prior id = parent_id;
