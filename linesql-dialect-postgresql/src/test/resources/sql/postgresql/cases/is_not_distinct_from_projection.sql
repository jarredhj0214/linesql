select
  e.current_value is not distinct from e.expected_value as values_match
from audit.events e
