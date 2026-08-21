create trigger app.users_bi
before insert on app.users
for each row
set new.created_at = now();
