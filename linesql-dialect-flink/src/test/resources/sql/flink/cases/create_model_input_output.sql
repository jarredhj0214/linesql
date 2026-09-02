CREATE MODEL ml.sentiment_model
INPUT (
  text STRING COMMENT 'input text',
  locale STRING
)
OUTPUT (
  label STRING COMMENT 'predicted label',
  score DOUBLE
)
WITH (
  'provider' = 'openai',
  'task' = 'classification'
)
