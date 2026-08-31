alter table mart.users
add column status varchar(32) not null default 'ACTIVE' comment 'user status' after name
