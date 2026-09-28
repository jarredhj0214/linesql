select (e.flags & e.mask) | e.default_flags as effective_flags,
       e.flags ^ e.changed_flags as changed_bits,
       ~e.disabled_flags as enabled_mask
from ods.events e
