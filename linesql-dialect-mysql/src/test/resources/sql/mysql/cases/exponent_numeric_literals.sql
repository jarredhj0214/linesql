select id, amount * 1.0e-3 as normalized_amount
from app.orders
where score >= .5e1 and ratio < 10.;
