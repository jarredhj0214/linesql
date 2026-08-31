update mart.users u
set status = default(status),
    updated_by = coalesce(updated_by, 'system')
where exists (
    select 1
    from app.user_events e
    where e.user_id = u.id
      and e.event_type = 'RESET'
)
