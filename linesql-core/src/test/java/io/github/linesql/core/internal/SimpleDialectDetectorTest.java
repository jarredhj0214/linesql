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
    }

    @Test
    public void detectsStarRocksAnchors() {
        assertFirst(SqlDialect.STARROCKS, "create table dwd.orders(id bigint) duplicate key(id) distributed by hash(id)");
        assertFirst(SqlDialect.STARROCKS, "create table agg_orders(id bigint) aggregate key(id)");
        assertFirst(SqlDialect.STARROCKS, "create table r(id bigint) properties (\"replication_num\" = \"1\")");
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
    public void detectsSparkAnchorsAndFallback() {
        assertFirst(SqlDialect.SPARK, "insert overwrite table ads.t select id from ods.s");
        assertFirst(SqlDialect.SPARK, "select id from ods.users lateral view explode(tags) x as tag");
        assertFirst(SqlDialect.SPARK, "create temporary view v as select id from ods.users");
        assertFirst(SqlDialect.SPARK, "select id from ods.users");
    }

    @Test
    public void doesNotMisclassifySparkMergeAsOracle() {
        List<SqlDialect> candidates = detector.detect(
                "merge into ads.users t using ods.users_delta s on t.id = s.id when matched then update set name = s.name");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.ORACLE));
    }

    @Test
    public void doesNotMisclassifyJsonPathArrayWildcardAsSqlServer() {
        List<SqlDialect> candidates = detector.detect(
                "select jt.item_id from ods.events, json_table(payload, '$.items[*]' columns (item_id string path '$.id')) jt");

        assertEquals(SqlDialect.SPARK, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.SQLSERVER));
    }

    @Test
    public void doesNotMisclassifyMySqlOnDuplicateKeyAsStarRocks() {
        List<SqlDialect> candidates = detector.detect(
                "insert into mart.t(c1) select a from app.s on duplicate key update c1 = values(c1)");

        assertEquals(SqlDialect.MYSQL, candidates.get(0));
        assertFalse(candidates.contains(SqlDialect.STARROCKS));
    }

    @Test
    public void returnsStructuredDetectionMetadata() {
        List<DialectCandidate> candidates = detector.detectCandidates(
                "create table ods_users(id bigint) with ('connector' = 'kafka')");

        assertFalse(candidates.isEmpty());
        assertEquals(SqlDialect.FLINK, candidates.get(0).getDialect());
        assertEquals(0.93, candidates.get(0).getConfidence(), 0.001);
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
