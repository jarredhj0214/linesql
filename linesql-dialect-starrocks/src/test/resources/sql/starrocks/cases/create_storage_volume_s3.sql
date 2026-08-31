create storage volume if not exists s3_volume
type = s3
locations = ('s3://warehouse/')
properties (
  "aws.s3.region" = "cn-north-1",
  "aws.s3.endpoint" = "s3.cn-north-1.amazonaws.com.cn"
)
