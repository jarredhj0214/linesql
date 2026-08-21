export table mart.orders
to "s3://bucket/export/orders/"
properties ("column_separator" = ",");
