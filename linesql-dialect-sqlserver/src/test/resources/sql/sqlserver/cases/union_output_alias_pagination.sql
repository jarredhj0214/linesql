SELECT a.id AS job_id
FROM dbo.jobs AS a
UNION ALL
SELECT b.id AS archived_id
FROM dbo.jobs_archive AS b
ORDER BY job_id OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY;
