create index idx_users_lower_email on app.users ((lower(email))) invisible;
