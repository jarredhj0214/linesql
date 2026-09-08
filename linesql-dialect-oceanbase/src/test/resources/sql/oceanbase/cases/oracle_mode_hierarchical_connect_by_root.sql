select connect_by_root name as root_name, id
from app.org_units
start with parent_id is null
connect by prior id = parent_id
