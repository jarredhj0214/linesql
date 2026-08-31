select ~flags as inverted_flags, flags | mask as combined_flags
from app.order_flags
where ~deleted_mask <> 0;
