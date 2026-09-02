CREATE MODEL ml.intent_model (
  sentence STRING COMMENT 'input text',
  label STRING COMMENT 'predicted label'
)
COMMENT 'intent classifier'
WITH ('provider' = 'openai')
