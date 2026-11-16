CREATE EXTERNAL MODEL encode_text AUTHORIZATION dbo
WITH (LOCATION = 'https://example.invalid/embed', API_FORMAT = 'OpenAI',
      MODEL_TYPE = EMBEDDINGS, MODEL = 'example', CREDENTIAL = service_credential);
