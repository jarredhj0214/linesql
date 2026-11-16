SELECT t.`true` AS flag_value FROM ods.flags t
WHERE COALESCE(t.enabled, NULL) = FALSE AND t.name IS NOT NULL;
