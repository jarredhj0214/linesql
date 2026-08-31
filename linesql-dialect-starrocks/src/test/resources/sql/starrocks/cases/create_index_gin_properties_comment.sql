create index idx_events_payload on ads.events(payload) using gin ("parser" = "english") comment 'payload inverted index'
