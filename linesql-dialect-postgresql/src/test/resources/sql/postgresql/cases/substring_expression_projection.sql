select substring(e.payload from e.start_pos for e.slice_length) as payload_slice
from mart.events e
