DELETE FROM ONLY mart.users u
USING staging.deleted_users d
WHERE u.id = d.id
  AND d.reason = 'gdpr';
