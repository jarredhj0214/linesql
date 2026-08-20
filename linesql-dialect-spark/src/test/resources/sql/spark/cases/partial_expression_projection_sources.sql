SELECT
  CASE
    WHEN l.label = d.feedback_tag OR updater_email = 'ops' THEN 1
    ELSE 0
  END AS matched
FROM ods.logs l
JOIN dim.feedback d ON l.order_code = d.order_code
