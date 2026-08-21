package io.github.linesql.dialect.spark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linesql.core.LineSql;
import io.github.linesql.core.model.ColumnUsage;
import io.github.linesql.core.model.LineageResult;
import io.github.linesql.core.model.SqlDialect;
import io.github.linesql.core.model.StatementType;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkDialectParserTest {
    @Test
    public void parsesSelectInputTable() {
        LineageResult result = LineSql.parse(sqlCase("select_basic"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(1, result.getInputTables().size());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertTrue(result.getOutputTables().isEmpty());
    }

    @Test
    public void parsesInsertInputAndOutputTables() {
        LineageResult result = LineSql.parse(sqlCase("insert_overwrite"));

        assertEquals(StatementType.INSERT, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("user_summary", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void parsesScriptStatements() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_semicolon"));

        assertEquals(2, results.size());
        assertEquals(StatementType.SELECT, results.get(0).getStatementType());
        assertEquals(StatementType.CREATE_TABLE_AS_SELECT, results.get(1).getStatementType());
        assertEquals("b", results.get(1).getOutputTables().get(0).getName());
    }

    @Test
    public void parsesJoinInputTables() {
        LineageResult result = LineSql.parse(sqlCase("join_basic"));

        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(2, result.getInputTables().size());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertEquals("orders", result.getInputTables().get(1).getName());
    }

    @Test
    public void parsesCreateViewOutputTable() {
        LineageResult result = LineSql.parse(sqlCase("create_view"));

        assertEquals(StatementType.CREATE_VIEW, result.getStatementType());
        assertEquals("mart", result.getOutputTables().get(0).getSchema());
        assertEquals("v_users", result.getOutputTables().get(0).getName());
        assertEquals("users", result.getInputTables().get(0).getName());
    }

    @Test
    public void reportsParseErrorsAsDiagnostics() {
        LineageResult result = LineSql.parse(sqlCase("parse_error"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals("SPARK_PARSE_ERROR", result.getDiagnostics().get(0).getCode());
    }

    @Test
    public void extractsPartialTableLineageOnParseError() {
        LineageResult result = LineSql.parse(
                "select id, name from ods.users where dt = '${yyyy-MM-dd}' and");

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals("SPARK_PARSE_ERROR", result.getDiagnostics().get(0).getCode());
        assertEquals("ods.users", tableNames(result.getInputTables()).get(0));
    }

    @Test
    public void parsesMergeIntoTable() {
        LineageResult result = LineSql.parse(sqlCase("merge_into"));

        assertEquals(StatementType.MERGE, result.getStatementType());
        assertEquals("ads", result.getOutputTables().get(0).getSchema());
        assertEquals("users", result.getOutputTables().get(0).getName());
        assertEquals("ods", result.getInputTables().get(0).getSchema());
    }

    @Test
    public void parsesCacheTableAsSelect() {
        LineageResult result = LineSql.parse(sqlCase("cache_table_as_select"));

        assertEquals(SqlDialect.SPARK, result.getDialect());
        assertEquals(StatementType.CACHE_TABLE, result.getStatementType());
        assertEquals("users", result.getInputTables().get(0).getName());
        assertEquals("cached_users", result.getOutputTables().get(0).getName());
        assertTrue(result.getDiagnostics().stream().noneMatch(d -> "SPARK_PARSE_ERROR".equals(d.getCode())));
    }

    @Test
    public void propagatesTemporaryViewAcrossScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_temp_view_lineage"));

        assertEquals(2, results.size());
        assertEquals(StatementType.CREATE_VIEW, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
        assertEquals("ads.user_summary", tableNames(results.get(1).getOutputTables()).get(0));
        assertEquals(2, results.get(1).getColumnLineage().size());
        assertEquals("ads.user_summary.user_id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(results.get(1).getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ads.user_summary.user_name", columnName(results.get(1).getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.name", columnName(results.get(1).getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void continuesAfterBadSqlInScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_bad_sql_recovery"));

        assertEquals(2, results.size());
        assertDiagnostics("script_bad_sql_recovery", singletonTextArray("SPARK_PARSE_ERROR"), results.get(0));
        assertEquals(StatementType.SELECT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
    }

    @Test
    public void dropsTemporaryViewFromScriptContext() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_drop_temp_view"));

        assertEquals(3, results.size());
        assertEquals(StatementType.CREATE_VIEW, results.get(0).getStatementType());
        assertEquals(StatementType.DROP_VIEW, results.get(1).getStatementType());
        assertEquals(StatementType.SELECT, results.get(2).getStatementType());
        assertEquals("tmp_users", tableNames(results.get(2).getInputTables()).get(0));
    }

    @Test
    public void propagatesCacheTableAcrossScript() {
        List<LineageResult> results = LineSql.parseScript(sqlCase("script_cache_table_lineage"));

        assertEquals(2, results.size());
        assertEquals(StatementType.CACHE_TABLE, results.get(0).getStatementType());
        assertEquals(StatementType.INSERT, results.get(1).getStatementType());
        assertEquals("ods.users", tableNames(results.get(1).getInputTables()).get(0));
        assertEquals("ads.cached_user_summary", tableNames(results.get(1).getOutputTables()).get(0));
        assertEquals("ads.cached_user_summary.user_id", columnName(results.get(1).getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(results.get(1).getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void representsTableStarColumnLineageWithoutMetadata() {
        LineageResult result = LineSql.parse("select * from ods.users");

        assertEquals(StatementType.SELECT, result.getStatementType());
        assertEquals(1, result.getColumnLineage().size());
        assertEquals("*.ods.users.*", result.getColumnLineage().get(0).getExpression() + "."
                + columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void expandsAliasedSubqueryStarColumnLineage() {
        LineageResult result = LineSql.parse(
                "select u.* from (select id as user_id, name from ods.users) u");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("name", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.name", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void expandsCteStarColumnLineage() {
        LineageResult result = LineSql.parse(
                "with u as (select id as user_id, name from ods.users) select * from u");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("name", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.name", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void resolvesDerivedColumnsCaseInsensitively() {
        LineageResult result = LineSql.parse(
                "with u as (select id as User_ID from ods.users) select user_id from u");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void preservesKnownDerivedColumnsWhenStarAlsoCarriesUnknownTableColumns() {
        LineageResult result = LineSql.parse(
                "select user_id "
                        + "from ("
                        + "select *, upper(name) as upper_name "
                        + "from (select id as user_id, name from ods.users) u "
                        + "left join dim.regions r on u.user_id = r.user_id"
                        + ") s");

        assertTrue(result.getColumnLineage().size() >= 1);
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void resolvesGeneratedColumnsCaseInsensitively() {
        LineageResult result = LineSql.parse(
                "select item from ods.orders lateral view explode(items) e as Item");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("item", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.orders.items", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void resolvesUnqualifiedProjectionFromUniqueDerivedRelationColumn() {
        LineageResult result = LineSql.parse(
                "select user_id, amount "
                        + "from (select id as user_id from ods.users) u "
                        + "join (select amount from dwd.orders) o on u.user_id = o.amount");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("amount", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("dwd.orders.amount", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void resolvesUnqualifiedProjectionFromUniqueBaseTableWhenDerivedColumnsAreKnown() {
        LineageResult result = LineSql.parse(
                "select id, name "
                        + "from ods.users u "
                        + "join (select order_id from dwd.orders) o on u.id = o.order_id");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("name", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.name", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void keepsUnqualifiedProjectionUnresolvedWhenDerivedRelationMayExposeColumn() {
        LineageResult result = LineSql.parse(
                "select id "
                        + "from ods.users u "
                        + "join (select id from dwd.orders) o on u.id = o.id");

        assertTrue(result.getColumnLineage().isEmpty());
    }

    @Test
    public void keepsUnqualifiedProjectionAmbiguousAcrossDerivedRelations() {
        LineageResult result = LineSql.parse(
                "select id "
                        + "from (select id from ods.users) u "
                        + "join (select id from dwd.orders) o on u.id = o.id");

        assertTrue(result.getColumnLineage().isEmpty());
    }

    @Test
    public void resolvesUnqualifiedProjectionFromUniqueDerivedWildcardSource() {
        LineageResult result = LineSql.parse(
                "select score "
                        + "from (select * from ods.events) e "
                        + "left join (select vin as vin_r from dim.vehicles) v on e.vin = v.vin_r");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("score", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.score", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void prefersExplicitDerivedColumnsOverAdjacentWildcardSources() {
        LineageResult result = LineSql.parse(
                "select vehicle_category_code, level_1_channel_code "
                        + "from (select vehicle_category_code, appoint_code from dwd.test_drive) t_test "
                        + "left join (select * from dwd.appoint_relation) t_appoint "
                        + "on t_test.appoint_code = t_appoint.appoint_code "
                        + "left join (select channel_code, first_channel_tag_code as level_1_channel_code "
                        + "from dim.channel) t2 on t_appoint.channel_code = t2.channel_code");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("vehicle_category_code", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("dwd.test_drive.vehicle_category_code", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("level_1_channel_code", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("dim.channel.first_channel_tag_code", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void resolvesBacktickQualifiedDirectProjection() {
        LineageResult result = LineSql.parse(
                "select t1.`department_id`, t2.`department_name` "
                        + "from eps_ods.ods_coa_staff_df t1 "
                        + "left join eps_dim.dim_coa_departments_wide_df t2 "
                        + "on t1.department_id = t2.department_id");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("department_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("eps_ods.ods_coa_staff_df.department_id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("department_name", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("eps_dim.dim_coa_departments_wide_df.department_name", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void keepsUnqualifiedProjectionAmbiguousAcrossDerivedWildcards() {
        LineageResult result = LineSql.parse(
                "select amount "
                        + "from (select * from ods.users) u "
                        + "join (select * from dwd.orders) o on u.id = o.user_id");

        assertTrue(result.getColumnLineage().isEmpty());
    }

    @Test
    public void preservesWildcardSourcesAcrossUnionStar() {
        LineageResult result = LineSql.parse("select * from ods.users union all select * from dwd.users");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("*", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.*", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("dwd.users.*", columnName(result.getColumnLineage().get(0).getSources().get(1)));
    }

    @Test
    public void resolvesDerivedColumnsFromUnionStarWildcardSources() {
        LineageResult result = LineSql.parse(
                "select *, end_time - collect_time as durs "
                        + "from (select * from ods.x union all select * from ods.w) s");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("*", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.x.*", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ods.w.*", columnName(result.getColumnLineage().get(0).getSources().get(1)));
        assertEquals("durs", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.x.end_time", columnName(result.getColumnLineage().get(1).getSources().get(0)));
        assertEquals("ods.w.end_time", columnName(result.getColumnLineage().get(1).getSources().get(1)));
        assertEquals("ods.x.collect_time", columnName(result.getColumnLineage().get(1).getSources().get(2)));
        assertEquals("ods.w.collect_time", columnName(result.getColumnLineage().get(1).getSources().get(3)));
    }

    @Test
    public void doesNotTreatCountStarAsWildcardProjection() {
        LineageResult result = LineSql.parse(
                "select dt, count(*) as cnt from ods.events group by dt");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("dt", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.dt", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("cnt", columnName(result.getColumnLineage().get(1).getTarget()));
        assertTrue(result.getColumnLineage().get(1).getSources().isEmpty());
    }

    @Test
    public void extractsSourcesFromAggregateExpressionContainingCountStar() {
        LineageResult result = LineSql.parse(
                "select cast(count(*) * (max(end_time) - min(collect_time)) / count(*) as int) as duration "
                        + "from ods.events");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("duration", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.end_time", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ods.events.collect_time", columnName(result.getColumnLineage().get(0).getSources().get(1)));
    }

    @Test
    public void extractsScalarSubqueryOnlyProjectionLineage() {
        LineageResult result = LineSql.parse(
                "with daily_amount as (select amount from ods.orders) "
                        + "select (select max(amount) from daily_amount) as max_amount");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("max_amount", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.orders.amount", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void keepsOuterExpressionSourcesWhenScalarSubqueryHasPredicateColumns() {
        LineageResult result = LineSql.parse(
                "select round(cnt / (select count(*) from ods.events where ev_soc > 0), 2) as ratio "
                        + "from (select count(*) as cnt from ods.events) s");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("ratio", columnName(result.getColumnLineage().get(0).getTarget()));
        assertTrue(result.getColumnLineage().get(0).getSources().isEmpty());
    }

    @Test
    public void keepsOuterLineageWhenHavingContainsSubquery() {
        LineageResult result = LineSql.parse(
                "select vin, min(dt) as min_dt from ("
                        + "select vin, dt from ods.events union all select vin, dt from dwd.events"
                        + ") s group by vin having vin in (select vin from dim.vehicles)");

        assertEquals(3, result.getInputTables().size());
        assertEquals(2, result.getColumnLineage().size());
        assertEquals("vin", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.vin", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("dwd.events.vin", columnName(result.getColumnLineage().get(0).getSources().get(1)));
        assertEquals("min_dt", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.events.dt", columnName(result.getColumnLineage().get(1).getSources().get(0)));
        assertEquals("dwd.events.dt", columnName(result.getColumnLineage().get(1).getSources().get(1)));
    }

    @Test
    public void resolvesUnqualifiedProjectionFromUniqueQualifiedJoinHint() {
        LineageResult result = LineSql.parse(
                "select count(case when kind_id = 6 then 1 end) as kind_cnt "
                        + "from ods.logs l join dim.kind d on l.kind_id = d.id");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("kind_cnt", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.logs.kind_id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void keepsPartialProjectionSourcesWhenSomeExpressionColumnsAreUnresolved() {
        LineageResult result = LineSql.parse(
                "select case when l.label = d.feedback_tag or updater_email = 'ops' then 1 else 0 end as matched "
                        + "from ods.logs l join dim.feedback d on l.order_code = d.order_code");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("matched", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.logs.label", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("dim.feedback.feedback_tag", columnName(result.getColumnLineage().get(0).getSources().get(1)));
        assertTrue(result.getDiagnostics().stream()
                .anyMatch(diagnostic -> "COLUMN_LINEAGE_PARTIAL".equals(diagnostic.getCode())));
    }

    @Test
    public void resolvesNestedFieldFromDerivedExpressionRoot() {
        LineageResult result = LineSql.parse(
                "select values.vin, values.timestamp as ts from ("
                        + "select from_json(content, 'vin STRING, timestamp BIGINT') as values "
                        + "from ods.events) s");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("vin", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.content", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ts", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.events.content", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void infersTargetColumnForUnaliasedSingleSourceExpression() {
        LineageResult result = LineSql.parse(
                "select cast(vin as string) from ods.events");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("vin", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.events.vin", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void keepsUnaliasedFunctionExpressionUnresolvedWithoutTargetName() {
        LineageResult result = LineSql.parse(
                "select lower(name) from ods.users");

        assertTrue(result.getColumnLineage().isEmpty());
    }

    @Test
    public void extractsMultiAliasFunctionOutputLineage() {
        LineageResult result = LineSql.parse(
                "select parse_user(id, name) as (user_id, user_name) from ods.users");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("user_id", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ods.users.name", columnName(result.getColumnLineage().get(0).getSources().get(1)));
        assertEquals("user_name", columnName(result.getColumnLineage().get(1).getTarget()));
        assertEquals("ods.users.id", columnName(result.getColumnLineage().get(1).getSources().get(0)));
        assertEquals("ods.users.name", columnName(result.getColumnLineage().get(1).getSources().get(1)));
    }

    @Test
    public void keepsUnaliasedMultiSourceExpressionUnresolvedWithoutTargetName() {
        LineageResult result = LineSql.parse(
                "select end_time - start_time from ods.events");

        assertTrue(result.getColumnLineage().isEmpty());
    }

    @Test
    public void mapsUnaliasedAggregateExpressionToExpressionTarget() {
        LineageResult result = LineSql.parse(
                "select max(amount) from ods.orders");

        assertEquals(1, result.getColumnLineage().size());
        assertEquals("max(amount)", columnName(result.getColumnLineage().get(0).getTarget()));
        assertEquals("ods.orders.amount", columnName(result.getColumnLineage().get(0).getSources().get(0)));
    }

    @Test
    public void mapsUnaliasedConstantExpressionToExpressionTarget() {
        LineageResult result = LineSql.parse(
                "select 'aaa', 1 from ods.users");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("'aaa'", columnName(result.getColumnLineage().get(0).getTarget()));
        assertTrue(result.getColumnLineage().get(0).getSources().isEmpty());
        assertEquals("1", columnName(result.getColumnLineage().get(1).getTarget()));
        assertTrue(result.getColumnLineage().get(1).getSources().isEmpty());
    }

    @Test
    public void keepsOuterProjectionLineageWhenWhereContainsSubquery() {
        LineageResult result = LineSql.parse(
                "select vin, sig_name from ods.events "
                        + "where vin in (select vin from dim.vehicles)");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("ods.events.vin", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("ods.events.sig_name", columnName(result.getColumnLineage().get(1).getSources().get(0)));
        assertEquals(2, result.getInputTables().size());
        assertEquals("dim.vehicles", tableNames(result.getInputTables()).get(1));
    }

    @Test
    public void resolvesFullyQualifiedColumnReferences() {
        LineageResult result = LineSql.parse(
                "select dm.dm_vom_basic.dt as dt, dm.dm_vom_basic.vin as vin from dm.dm_vom_basic");

        assertEquals(2, result.getColumnLineage().size());
        assertEquals("dm.dm_vom_basic.dt", columnName(result.getColumnLineage().get(0).getSources().get(0)));
        assertEquals("dm.dm_vom_basic.vin", columnName(result.getColumnLineage().get(1).getSources().get(0)));
    }

    @Test
    public void parsesNonLineageStatementsWithoutDiagnostics() {
        assertNonLineageStatement("use_database", StatementType.USE_SCHEMA);
        assertNonLineageStatement("set_catalog", StatementType.CONTROL);
        assertNonLineageStatement("reset_configuration", StatementType.CONTROL);
        assertNonLineageStatement("create_namespace", StatementType.CREATE_SCHEMA);
        assertNonLineageStatement("drop_namespace", StatementType.DROP_SCHEMA);
        assertNonLineageStatement("show_namespaces", StatementType.READ_METADATA);
        assertNonLineageStatement("show_catalogs", StatementType.READ_METADATA);
        assertNonLineageStatement("analyze_tables", StatementType.READ_METADATA);
        assertNonLineageStatement("create_function", StatementType.CREATE_ROUTINE);
        assertNonLineageStatement("create_udf_return_query", StatementType.CREATE_ROUTINE);
        assertNonLineageStatement("drop_function", StatementType.DROP_ROUTINE);
        assertNonLineageStatement("call_procedure", StatementType.CONTROL);
        assertNonLineageStatement("show_functions", StatementType.READ_METADATA);
        assertNonLineageStatement("describe_function", StatementType.READ_METADATA);
        assertNonLineageStatement("create_variable", StatementType.CONTROL);
        assertNonLineageStatement("declare_cursor", StatementType.CONTROL);
        assertNonLineageStatement("show_tables", StatementType.READ_METADATA);
        assertNonLineageStatement("show_views", StatementType.READ_METADATA);
        assertNonLineageStatement("show_collations", StatementType.READ_METADATA);
        assertNonLineageStatement("describe_namespace", StatementType.READ_METADATA);
        assertNonLineageStatement("comment_namespace", StatementType.CONTROL);
        assertNonLineageStatement("refresh_resource", StatementType.READ_METADATA);
        assertNonLineageStatement("clear_cache", StatementType.UNCACHE_TABLE);
        assertNonLineageStatement("add_jar_resource", StatementType.CONTROL);
    }

    @Test
    public void parsesTableMaintenanceStatementsWithoutColumnLineageDiagnostics() {
        assertTableOnlyStatementWithoutDiagnostics("refresh table mart.users", StatementType.READ_METADATA);
        assertTableOnlyStatementWithoutDiagnostics("alter table mart.users set tblproperties('k'='v')",
                StatementType.ALTER_TABLE);
        assertTableOnlyStatementWithoutDiagnostics("truncate table mart.users", StatementType.TRUNCATE_TABLE);
        assertTableOnlyStatementWithoutDiagnostics("drop table mart.users", StatementType.DROP_TABLE);
        assertTableOnlyStatementWithoutDiagnostics("alter table mart.users rename to mart.users_archive",
                StatementType.RENAME_TABLE);
    }

    @Test
    public void manifestReferencesExistingSqlFiles() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/spark/manifest.json"));

        assertEquals("SPARK", manifest.get("dialect").asText());
        for (JsonNode sqlCase : manifest.get("cases")) {
            String file = sqlCase.get("file").asText();
            assertTrue("Missing SQL case file: " + file, resourceExists("/sql/spark/" + file));
        }
    }

    @Test
    public void manifestCasesMatchExpectedLineage() throws IOException {
        JsonNode manifest = new ObjectMapper().readTree(resource("/sql/spark/manifest.json"));

        for (JsonNode sqlCase : manifest.get("cases")) {
            String caseId = sqlCase.get("id").asText();
            String sql = resource("/sql/spark/" + sqlCase.get("file").asText());
            String statementType = sqlCase.get("statementType").asText();

            if ("MULTI".equals(statementType)) {
                List<LineageResult> results = LineSql.parseScript(sql);
                assertTables(caseId, sqlCase.get("inputTables"), collectInputTables(results));
                assertTables(caseId, sqlCase.get("outputTables"), collectOutputTables(results));
                continue;
            }

            LineageResult result = LineSql.parse(sql);
            if ("ERROR".equals(statementType)) {
                assertDiagnostics(caseId, sqlCase.get("expectedDiagnostics"), result);
                continue;
            }

            assertEquals(caseId, StatementType.valueOf(statementType), result.getStatementType());
            assertTables(caseId, sqlCase.get("inputTables"), tableNames(result.getInputTables()));
            assertTables(caseId, sqlCase.get("outputTables"), tableNames(result.getOutputTables()));
            if (sqlCase.has("columnLineage")) {
                assertColumnLineage(caseId, sqlCase.get("columnLineage"), result);
            }
            if (sqlCase.has("columnUsages")) {
                assertColumnUsages(caseId, sqlCase.get("columnUsages"), result);
            }
            if (sqlCase.has("expectedDiagnostics")) {
                assertDiagnostics(caseId, sqlCase.get("expectedDiagnostics"), result);
            }
        }
    }

    private static String sqlCase(String caseId) {
        String path = "/sql/spark/cases/" + caseId + ".sql";
        try {
            return resource(path);
        } catch (IOException e) {
            throw new AssertionError("Failed to read SQL case resource: " + path, e);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = SparkDialectParserTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            byte[] bytes = readAllBytes(input);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean resourceExists(String path) {
        try (InputStream input = SparkDialectParserTest.class.getResourceAsStream(path)) {
            return input != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static void assertTables(String caseId, JsonNode expectedNode, List<String> actual) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.asText()));
        assertEquals(caseId, expected, actual);
    }

    private static void assertDiagnostics(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.asText()));
        List<String> actual = result.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getCode())
                .collect(Collectors.toList());
        assertTrue(caseId + " diagnostics " + actual + " did not include " + expected, actual.containsAll(expected));
    }

    private static void assertNonLineageStatement(String caseId, StatementType statementType) {
        LineageResult result = LineSql.parse(sqlCase(caseId));
        assertEquals(caseId, statementType, result.getStatementType());
        assertTrue(caseId, result.getInputTables().isEmpty());
        assertTrue(caseId, result.getOutputTables().isEmpty());
        assertTrue(caseId, result.getColumnLineage().isEmpty());
        assertTrue(caseId, result.getDiagnostics().isEmpty());
    }

    private static void assertTableOnlyStatementWithoutDiagnostics(String sql, StatementType statementType) {
        LineageResult result = LineSql.parse(sql);
        assertEquals(sql, statementType, result.getStatementType());
        assertTrue(sql, !result.getInputTables().isEmpty() || !result.getOutputTables().isEmpty());
        assertTrue(sql, result.getColumnLineage().isEmpty());
        assertTrue(sql, result.getDiagnostics().isEmpty());
    }

    private static JsonNode singletonTextArray(String value) {
        return new ObjectMapper().createArrayNode().add(value);
    }

    private static List<String> collectInputTables(List<LineageResult> results) {
        Set<String> tables = new LinkedHashSet<>();
        results.forEach(result -> tables.addAll(tableNames(result.getInputTables())));
        return new ArrayList<>(tables);
    }

    private static List<String> collectOutputTables(List<LineageResult> results) {
        Set<String> tables = new LinkedHashSet<>();
        results.forEach(result -> tables.addAll(tableNames(result.getOutputTables())));
        return new ArrayList<>(tables);
    }

    private static List<String> tableNames(List<io.github.linesql.core.model.TableRef> tables) {
        return tables.stream()
                .map(SparkDialectParserTest::tableName)
                .collect(Collectors.toList());
    }

    private static String tableName(io.github.linesql.core.model.TableRef table) {
        List<String> parts = new ArrayList<>();
        if (table.getCatalog() != null) {
            parts.add(table.getCatalog());
        }
        if (table.getSchema() != null) {
            parts.add(table.getSchema());
        }
        parts.add(table.getName());
        return String.join(".", parts);
    }

    private static void assertColumnLineage(String caseId, JsonNode expectedNode, LineageResult result) {
        assertEquals(caseId, expectedNode.size(), result.getColumnLineage().size());
        for (int i = 0; i < expectedNode.size(); i++) {
            JsonNode expected = expectedNode.get(i);
            io.github.linesql.core.model.ColumnLineage actual = result.getColumnLineage().get(i);
            assertEquals(caseId, expected.get("target").asText(), columnName(actual.getTarget()));
            List<String> expectedSources = new ArrayList<>();
            expected.get("sources").forEach(node -> expectedSources.add(node.asText()));
            List<String> actualSources = actual.getSources().stream()
                    .map(SparkDialectParserTest::columnName)
                    .collect(Collectors.toList());
            assertEquals(caseId, expectedSources, actualSources);
        }
    }

    private static void assertColumnUsages(String caseId, JsonNode expectedNode, LineageResult result) {
        List<String> expected = new ArrayList<>();
        expectedNode.forEach(node -> expected.add(node.get("type").asText() + ":" + node.get("column").asText()));
        List<String> actual = result.getColumnUsages().stream()
                .map(SparkDialectParserTest::columnUsageName)
                .collect(Collectors.toList());
        assertEquals(caseId, expected, actual);
    }

    private static String columnUsageName(ColumnUsage usage) {
        return usage.getType().name() + ":" + columnName(usage.getColumn());
    }

    private static String columnName(io.github.linesql.core.model.ColumnRef column) {
        if (column.getTable() == null) {
            return column.getName();
        }
        return tableName(column.getTable()) + "." + column.getName();
    }
}
