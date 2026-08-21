export table mart.orders partition(dt)
to "s3://bucket/export/orders/dt=2026-08-21/"
properties ("line_delimiter" = "\n");
