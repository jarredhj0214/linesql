alter table mart.user_profile
  add column age int,
  modify column user_name varchar(256),
  drop column email;
