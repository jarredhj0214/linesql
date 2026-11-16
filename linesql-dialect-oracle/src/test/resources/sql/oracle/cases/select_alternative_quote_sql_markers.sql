SELECT o.id FROM ods.orders o
WHERE o.note = q'[It's paid; /*!80000 DELETE FROM ghost.orders */ $tag$]';
