WITH features AS (
    SELECT e.user_id, e.amount + 1 AS model_feature
    FROM dwd.events e
    WHERE e.ready
)
SELECT p.result_id, p.score
FROM TABLE(ML_PREDICT(TABLE features, MODEL ml.churn, DESCRIPTOR(model_feature)))
AS p(result_id, derived_feature, score)
WHERE p.score > 0.5;
