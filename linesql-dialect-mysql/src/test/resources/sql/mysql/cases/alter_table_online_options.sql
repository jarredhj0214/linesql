alter table mart.users
  add column nickname varchar(128),
  algorithm=inplace,
  lock=none;
