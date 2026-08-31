select
  cast(amount as signed integer) as signed_amount,
  cast(score as unsigned) as unsigned_score
from app.metrics;
