create warehouse if not exists wh_etl
properties (
  "min_cluster" = "1",
  "max_cluster" = "3",
  "size" = "medium"
);
