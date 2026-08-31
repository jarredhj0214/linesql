select id1
from mart.t_match
where value match "starrocks"
  and not value_test match_any "spark flink"
  and content match_all "lakehouse analytics";
