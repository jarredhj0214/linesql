select sum_c1_1, avg_c2_2
from t1
pivot (
  sum(c1) as sum_c1,
  avg(c2) as avg_c2
  for c3 in (1, 2)
);
