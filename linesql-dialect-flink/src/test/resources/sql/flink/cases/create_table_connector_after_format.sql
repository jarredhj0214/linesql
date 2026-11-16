CREATE TABLE app.events (id BIGINT)
WITH ('format'='json', 'connector.type'='kafka', 'topic'='events');
