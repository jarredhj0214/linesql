select
  id,
  x'0A0B' as hex_blob,
  0xFF as hex_num,
  b'1010' as bit_text,
  0b1011 as bit_num
from app.events
where payload_hash = x'CAFE';
