alter table mart.users replace columns (
  user_id bigint,
  profile struct<email:string,tags:array<string>>
) cascade
