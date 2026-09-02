CREATE FUNCTION udf.normalize_phone
AS com.example.flink.udf.NormalizePhone
LANGUAGE JAVA
USING JAR 'file:///opt/flink/usrlib/udf.jar'
