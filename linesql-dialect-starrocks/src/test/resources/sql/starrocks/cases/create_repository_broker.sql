create read only repository repo_s3
with broker
on location "s3://bucket/starrocks/repo"
properties ("aws.s3.endpoint" = "s3.amazonaws.com");
