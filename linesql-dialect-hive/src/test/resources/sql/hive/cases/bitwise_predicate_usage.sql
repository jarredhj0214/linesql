select e.id
from ods.events e
where (e.flags & e.required_flags) = e.required_flags
