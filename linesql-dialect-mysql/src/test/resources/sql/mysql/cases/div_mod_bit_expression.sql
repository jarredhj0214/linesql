select amount div quantity as unit_amount,
       score mod 10 as score_bucket,
       flags & 4 as has_flag
from app.orders;
