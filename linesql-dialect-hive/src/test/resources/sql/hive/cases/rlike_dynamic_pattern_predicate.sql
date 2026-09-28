select e.id
from ods.events e
where e.event_name rlike e.allowed_pattern
