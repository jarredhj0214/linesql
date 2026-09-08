insert into audit.proc_result (id, status)
exec dbo.load_recent_orders @dt = '2026-09-08';
