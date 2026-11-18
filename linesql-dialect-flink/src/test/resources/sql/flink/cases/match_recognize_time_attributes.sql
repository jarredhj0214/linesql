SELECT mr.matched_at, mr.explicit_time, mr.processed_at
FROM dwd.events MATCH_RECOGNIZE (
  ORDER BY event_time, id
  MEASURES MATCH_ROWTIME() AS matched_at,
           MATCH_ROWTIME(A.event_time) AS explicit_time,
           MATCH_PROCTIME() AS processed_at
  ONE ROW PER MATCH
  PATTERN (A B)
  DEFINE A AS event_type = 'start', B AS event_type = 'end'
) AS mr;
