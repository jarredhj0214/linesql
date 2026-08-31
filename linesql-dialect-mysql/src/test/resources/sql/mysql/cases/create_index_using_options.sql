create index idx_users_status
using btree
on app.users(status, created_at desc)
visible
algorithm=inplace
lock=none;
