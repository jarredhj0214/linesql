ALTER EXTERNAL MODEL encode_text
SET (LOCATION = 'https://example.invalid/new', API_FORMAT = 'OpenAI',
     MODEL_TYPE = EMBEDDINGS, MODEL = 'example', PARAMETERS = N'{"dimensions":32}');
