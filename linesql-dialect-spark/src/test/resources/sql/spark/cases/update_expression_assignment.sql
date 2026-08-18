update ads.users
set name = upper(name),
    score = score + bonus_score
where dt = '20260101'
