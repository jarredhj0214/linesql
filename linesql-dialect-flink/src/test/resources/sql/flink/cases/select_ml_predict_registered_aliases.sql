CREATE TABLE dwd.features (user_id BIGINT, age INT, income DOUBLE) WITH ('connector' = 'datagen');
SELECT p.user_key, p.score
FROM ML_PREDICT(TABLE dwd.features, MODEL ml.churn, DESCRIPTOR(age, income))
AS p(user_key, years, earnings, score)
WHERE p.score > 0.5
ORDER BY 2;
