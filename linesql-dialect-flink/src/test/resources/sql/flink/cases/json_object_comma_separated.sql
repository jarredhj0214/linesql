SELECT
  JSON_OBJECT(
    'caller' VALUE caller,
    'businessTime' VALUE CAST(event_time AS STRING),
    'data' VALUE JSON_OBJECT('vin' VALUE vin, 'speed' VALUE speed)
  ) AS payload
FROM dwd.events
