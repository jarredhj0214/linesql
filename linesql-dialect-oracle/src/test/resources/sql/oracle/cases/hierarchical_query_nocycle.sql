select id, parent_id
from app.org_units
start with parent_id is null
connect by nocycle prior id = parent_id
