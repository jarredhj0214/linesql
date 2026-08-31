alter table mart.users
change column nickname display_name varchar(128) null default null comment 'display name' after name;
