analyze table ads.users update histogram on region, channel
with async mode
with 32 buckets
properties (
  "histogram_sample_ratio" = "0.5"
);
