create external table iceberg_tbl
(
  id bigint null,
  data varchar(200) null
)
engine = iceberg
properties
(
  "resource" = "iceberg0",
  "database" = "iceberg",
  "table" = "iceberg_table"
);
