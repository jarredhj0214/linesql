SELECT q.id AS pending_id, p.owner AS partition_owner
FROM queue.pending AS q
JOIN queue.partitions AS p ON q.partition_id = p.id
WHERE q.state = 'ready'
ORDER BY q.priority, q.id
FOR NO KEY UPDATE OF q SKIP LOCKED
FOR KEY SHARE OF p NOWAIT;
