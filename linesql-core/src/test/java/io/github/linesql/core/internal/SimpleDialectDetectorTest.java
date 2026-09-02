package io.github.linesql.core.internal;

import io.github.linesql.core.model.DialectCandidate;
import io.github.linesql.core.model.SqlDialect;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimpleDialectDetectorTest {
    private final SimpleDialectDetector detector = new SimpleDialectDetector();

    @Test
    public void detectsMySqlAnchors() {
        assertFirst(SqlDialect.MYSQL, "replace into mart.t(c1) select a from app.s");
        assertFirst(SqlDialect.MYSQL, "insert into mart.t(c1) select a from app.s on duplicate key update c1 = values(c1)");
        assertFirst(SqlDialect.MYSQL, "select id from app.users limit 10, 20");
        assertFirst(SqlDialect.MYSQL, "update mart.t join app.s on t.id = s.id set t.c = s.c");
        assertFirst(SqlDialect.MYSQL, "rename table app.old_users to app.users");
        assertFirst(SqlDialect.MYSQL, "lock tables app.users read, mart.user_summary write");
        assertFirst(SqlDialect.MYSQL, "select payload->>'$.id' from app.events");
        assertFirst(SqlDialect.MYSQL, "select id into outfile '/tmp/users.csv' from app.users");
        assertFirst(SqlDialect.MYSQL, "alter algorithm = merge view mart.v as select id from app.users");
        assertFirst(SqlDialect.MYSQL, "update low_priority ignore mart.t set c = 1");
        assertFirst(SqlDialect.MYSQL, "delete low_priority quick ignore from mart.t where id = 1");
        assertFirst(SqlDialect.MYSQL, "select jt.sku from app.orders u join json_table(u.payload, '$.items[*]' columns (sku varchar(64) path '$.sku')) jt");
        assertFirst(SqlDialect.MYSQL, "load xml local infile '/tmp/events.xml' into table ods.events rows identified by '<event>' (event_id)");
    }

    @Test
    public void detectsHiveAnchors() {
        assertFirst(SqlDialect.HIVE, "create table ods.users(id bigint) stored as parquet");
        assertFirst(SqlDialect.HIVE, "create table ods.users(id bigint) row format delimited fields terminated by ','");
        assertFirst(SqlDialect.HIVE, "create table ods.users(id bigint) clustered by (id) into 8 buckets");
    }

    @Test
    public void detectsFlinkAnchors() {
        assertFirst(SqlDialect.FLINK, "create table ods_users(id bigint) with ('connector' = 'kafka')");
        assertFirst(SqlDialect.FLINK, "create table events(ts timestamp(3), watermark for ts as ts - interval '5' second)");
        assertFirst(SqlDialect.FLINK, "compile plan '/tmp/p.json' for insert into sink select id from source");
        assertFirst(SqlDialect.FLINK, "execute plan '/tmp/p.json'");
        assertFirst(SqlDialect.FLINK, "show jobs");
        assertFirst(SqlDialect.FLINK, "stop job '8e0d' with savepoint with drain");
        assertFirst(SqlDialect.FLINK, "call `system`.generate_n(4)");
        assertFirst(SqlDialect.FLINK, "set table.exec.sink.not-null-enforcer=drop");
        assertFirst(SqlDialect.FLINK, "set 'table.exec.state.ttl'='30d'");
        assertFirst(SqlDialect.FLINK, "reset table.exec.source.cdc-events-duplicate");
        assertFirst(SqlDialect.FLINK, "create catalog myhive with ('type' = 'hive')");
        assertFirst(SqlDialect.FLINK, "create catalog c with ('type' = 'paimon'); use catalog c; insert into sink /*+ options('sink.parallelism'='2') */ select json_value(payload, '$.id' returning string) from src");
        assertFirst(SqlDialect.FLINK, "insert into sink /*+ options('sink.parallelism'='2') */ select id from src");
        assertFirst(SqlDialect.FLINK, "select json_query(payload, '$.items' returning array<string>) from src");
        assertFirst(SqlDialect.FLINK, "select floor(ts to minute), extract(epoch from ts) from src");
        assertFirst(SqlDialect.FLINK, "select tumble_start(proc_time, interval '1' minute) from src");
        assertFirst(SqlDialect.FLINK, "select * from table(hop(table src, descriptor(ts), interval '1' minute, interval '1' hour))");
        assertFirst(SqlDialect.FLINK, "create table t(id int) with ('connector.type' = 'jdbc')");
        assertFirst(SqlDialect.FLINK, "select * from src for system_time as of proctime()");
        assertFirst(SqlDialect.FLINK, "create temporary view v as select split_index(source, '/', 1) as p from src");
        assertFirst(SqlDialect.FLINK, "create view if not exist dwd.v as select id from ods.s");
        assertFirst(SqlDialect.FLINK, "create model ml.m with ('provider' = 'openai')");
        assertFirst(SqlDialect.FLINK, "create materialized table mart.t as select id from ods.s");
    }

    @Test
    public void detectsStarRocksAnchors() {
        assertFirst(SqlDialect.STARROCKS, "create table dwd.orders(id bigint) duplicate key(id) distributed by hash(id)");
        assertFirst(SqlDialect.STARROCKS, "create table agg_orders(id bigint) aggregate key(id)");
        assertFirst(SqlDialect.STARROCKS, "create table r(id bigint) properties (\"replication_num\" = \"1\")");
        assertFirst(SqlDialect.STARROCKS, "create routine load mart.job on ods.events from kafka (\"kafka_topic\" = \"events\")");
        assertFirst(SqlDialect.STARROCKS, "load label mart.job (data infile (\"s3://bucket/*.csv\") into table ods.events)");
        assertFirst(SqlDialect.STARROCKS, "refresh materialized view mart.mv_events with sync mode");
    }

    @Test
    public void detectsOracleAnchors() {
        assertFirst(SqlDialect.ORACLE, "select id from dual");
        assertFirst(SqlDialect.ORACLE, "select id from org start with parent_id is null connect by prior id = parent_id");
    }

    @Test
    public void detectsSqlServerAnchors() {
        assertFirst(SqlDialect.SQLSERVER, "select top 10 id from dbo.users");
        assertFirst(SqlDialect.SQLSERVER, "select [用户ID] from [业务库].[用户表]");
        assertFirst(SqlDialect.SQLSERVER, "select id from dbo.users with (nolock)");
    }

    @Test
    public void detectsPostgreSqlAnchors() {
        assertFirst(SqlDialect.POSTGRESQL, "insert into public.users(id) values (1) on conflict (id) do nothing");
        assertFirst(SqlDialect.POSTGRESQL, "update public.users set name = 'a' returning id");
        assertFirst(SqlDialect.POSTGRESQL, "select id::text from public.users where name ilike 'a%'");
    }

    @Test
    public void detectsOceanBaseAnchors() {
        assertFirst(SqlDialect.OCEANBASE, "select id from oceanbase.__all_virtual_table");
    }

    @Test
    public void detectsSparkAnchorsAndFallback() {
        assertFirst(SqlDialect.SPARK, "insert overwrite table ads.t select id from ods.s");
        assertFirst(SqlDialect.SPARK, "select id from ods.users lateral view explode(tags) x as tag");
        assertFirst(SqlDialect.SPARK, "create temporary view v as select id from ods.users");
        assertFirst(SqlDialect.SPARK, "select id from ods.users");
    }

    @Test
    public void prefersSparkForLambdaHighOrderFunctions() {
        assertFirst(SqlDialect.SPARK, "select transform(items, x -> x.id) as ids from ods.events");
        assertFirst(SqlDialect.SPARK, "select filter(signal_list, x -> x is not null) as signals from ods.events");
        assertFirst(SqlDialect.SPARK, "select array_sort(items, (x, y) -> case when x.rank < y.rank then -1 else 1 end) as sorted_items from ods.events");
    }

    @Test
    public void distinguishesMysqlJsonArrowFromSparkLambdaArrow() {
        assertFirst(SqlDialect.MYSQL, "select payload->>'$.id' as event_id from app.events");
        assertFirst(SqlDialect.MYSQL, "select payload->'$.items[0]' as first_item from app.events");
        assertFirst(SqlDialect.SPARK, "select transform(from_json(payload, 'array<struct<id:string>>'), x -> x.id) as ids from ods.events");
    }

    @Test
    public void prefersSparkWhenDivAppearsWithSparkSignals() {
        assertFirst(SqlDialect.SPARK,
                "select map_res['vin'] as vin, from_unixtime(cast(time_nano as bigint) div 1000000000, 'yyyy-MM-dd') as time_format "
                        + "from ods.events where dt = '${yyyy-MM-dd}'");
        assertFirst(SqlDialect.MYSQL, "select amount div quantity as bucket from app.orders");
    }

    @Test
    public void doesNotMisclassifySparkMergeAsOracle() {
        List<SqlDialect> candidates = detector.detect(
                "merge into ads.users t using ods.users_delta s on t.id = s.id when matched then update set name = s.name");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.ORACLE));
        assertFalse(candidates.contains(SqlDialect.SQLSERVER));
    }

    @Test
    public void doesNotMisclassifyJsonPathArrayWildcardAsSqlServer() {
        List<SqlDialect> candidates = detector.detect(
                "select jt.item_id from ods.events, json_table(payload, '$.items[*]' columns (item_id string path '$.id')) jt");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.SQLSERVER));
    }

    @Test
    public void keepsMysqlJsonTableWithMysqlColumnTypesAheadOfSparkFallback() {
        assertFirst(SqlDialect.MYSQL,
                "select jt.sku from app.orders u join json_table(u.payload, '$.items[*]' columns (sku varchar(64) path '$.sku')) jt");
    }

    @Test
    public void doesNotMisclassifySparkMapSubscriptAsSqlServer() {
        List<SqlDialect> candidates = detector.detect(
                "select cast(t_form.check_extend_filed_map[t_extend.extend_filed_name] as string) as extend_value "
                        + "from eps_ods.ods_form t_form "
                        + "left join eps_dim.dim_extend t_extend on t_form.id = t_extend.form_id "
                        + "where t_form.dt = '${yyyy-MM-dd}'");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.SQLSERVER));
    }

    @Test
    public void prefersSparkForCommonProductionFunctionsAndVariables() {
        List<SqlDialect> candidates = detector.detect(
                "select get_json_object(event_content, '$.Spark Properties.spark.app.name') as app_name, "
                        + "regexp_replace(part_code_link, '[0-9]+::', '') as part_code, "
                        + "date_format(fault_time, 'yyyy-MM-dd') as fault_dt "
                        + "from eps_ods.ods_event_log "
                        + "where dt = '${yyyy-MM-dd}'");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
    }

    @Test
    public void treatsObStyleIdentifierAsWeakOceanBaseSignal() {
        List<SqlDialect> candidates = detector.detect(
                "select ob_level, vin from eps_ods.ods_vehicle_ob_signal where dt = '${yyyy-MM-dd}'");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertTrue(candidates.contains(SqlDialect.OCEANBASE));
    }

    @Test
    public void doesNotInferOceanBaseOnlyFromObSubstringInSparkSchemaName() {
        List<SqlDialect> candidates = detector.detect(
                "select id from cop_apass_ob_prod.cop_consistent_dts_size where is_del = 0");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertTrue(candidates.contains(SqlDialect.OCEANBASE));
    }

    @Test
    public void prefersSparkForDistributeSortByAndRlike() {
        List<SqlDialect> candidates = detector.detect(
                "select * from app.events where package_name rlike '^[a-zA-Z0-9.]+$' distribute by vin sort by vin, event_time");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
    }

    @Test
    public void doesNotMisclassifyMySqlOnDuplicateKeyAsStarRocks() {
        List<SqlDialect> candidates = detector.detect(
                "insert into mart.t(c1) select a from app.s on duplicate key update c1 = values(c1)");

        assertEquals(SqlDialect.MYSQL, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.STARROCKS));
    }

    @Test
    public void prefersSparkForQualifyCompatibilitySyntax() {
        List<SqlDialect> candidates = detector.detect(
                "select id::varchar from ods.user_events where event_name ilike '%login%' qualify row_number() over(order by event_time) = 1");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertTrue(candidates.contains(SqlDialect.POSTGRESQL));
    }

    @Test
    public void ignoresDialectAnchorsInsideComments() {
        List<SqlDialect> candidates = detector.detect(
                "select id from ods.users\n"
                        + "-- delete from t join s on t.id = s.id\n"
                        + "where status != 'delete from comment text'");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.MYSQL));
    }

    @Test
    public void detectsMysqlExecutableVersionComments() {
        assertFirst(SqlDialect.MYSQL, "/*!80000 select id from app.users where status = 'ACTIVE' */");
        assertFirst(SqlDialect.MYSQL, "/*!40101 set names utf8mb4 */");
    }

    @Test
    public void returnsStructuredDetectionMetadata() {
        List<DialectCandidate> candidates = detector.detectCandidates(
                "create table ods_users(id bigint) with ('connector' = 'kafka')");

        assertFalse(candidates.isEmpty());
        assertEquals(SqlDialect.FLINK, candidates.get(0).getDialect());
        assertEquals(0.98, candidates.get(0).getConfidence(), 0.001);
        assertTrue(candidates.get(0).getReason().contains("Flink"));
    }

    @Test
    public void returnsFallbackReasonForDialectNeutralSql() {
        DialectCandidate candidate = detector.detectCandidates("select id from ods.users").get(0);

        assertEquals(SqlDialect.SPARK, candidate.getDialect());
        assertEquals(0.50, candidate.getConfidence(), 0.001);
        assertTrue(candidate.getReason().contains("fallback"));
    }

    private void assertFirst(SqlDialect expected, String sql) {
        List<SqlDialect> candidates = detector.detect(sql);

        assertFalse(candidates.isEmpty());
        assertEquals(expected, candidates.get(0));
        assertTrue(candidates.contains(SqlDialect.SPARK));
    }
}
