from ods.users u
insert overwrite table ads.active_users(user_id, user_name)
select u.id, u.name
where u.status = 'ACTIVE'
insert into table audit.user_emails(user_id, email_address)
select u.id, lower(u.email)
where u.email is not null
