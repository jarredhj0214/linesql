select e.id
from audit.events e
where e.payload is distinct from e.previous_payload
