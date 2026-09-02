create external catalog jdbc_mysql
properties
(
  "type" = "jdbc",
  "user" = "root",
  "password" = "secret",
  "jdbc_uri" = "jdbc:mysql://127.0.0.1:3306",
  "driver_url" = "https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.28/mysql-connector-java-8.0.28.jar",
  "driver_class" = "com.mysql.cj.jdbc.Driver"
);
