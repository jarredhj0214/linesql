create index mart.idx_users_email_phone
on mart.users (lower(email), substr(phone, 1, 3));
