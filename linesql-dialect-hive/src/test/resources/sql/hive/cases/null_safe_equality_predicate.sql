select e.id
from ods.events e
where e.actual_value <=> e.expected_value
