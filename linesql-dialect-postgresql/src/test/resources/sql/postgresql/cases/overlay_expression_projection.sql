select overlay(e.payload placing e.replacement from e.start_pos for e.replace_length) as patched_payload
from mart.events e
