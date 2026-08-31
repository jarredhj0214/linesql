select
  char(ascii_code using utf8mb4) as decoded_char,
  concat_ws('-', region, char(status_code using utf8mb4)) as region_status
from app.status_codes
where char(flag_code using utf8mb4) = 'Y'

