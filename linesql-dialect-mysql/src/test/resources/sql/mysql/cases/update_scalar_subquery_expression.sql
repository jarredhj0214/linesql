update mart.users u
set u.score = coalesce(
        (select max(s.score)
         from app.user_scores s
         where s.user_id = u.id),
        u.score
    ),
    u.updated_at = current_timestamp()
where u.status = 'ACTIVE'
