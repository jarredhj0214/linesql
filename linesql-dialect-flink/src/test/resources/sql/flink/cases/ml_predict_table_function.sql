SELECT user_id, prediction
FROM ML_PREDICT(
  TABLE dwd.user_features,
  MODEL ml.churn_model,
  DESCRIPTOR(age),
  CONFIG => MAP['async', 'true']
) AS p(user_id, prediction)
