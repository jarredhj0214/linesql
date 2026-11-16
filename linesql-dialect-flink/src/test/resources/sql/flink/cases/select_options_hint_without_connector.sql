SELECT id FROM app.events /*+ OPTIONS('scan.startup.mode'='latest-offset') */;
