select trim(both e.trim_chars from e.raw_value) as normalized_value
from mart.events e
