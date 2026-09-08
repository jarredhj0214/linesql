create table dbo.users_computed (
    first_name varchar(50),
    last_name varchar(50),
    full_name as first_name + ' ' + last_name persisted
);
