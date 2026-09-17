package com.gis.servelq.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.servelq.dto.AnalyticsRecordDTO;
import com.gis.servelq.dto.ModelInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AiForecastRepository {

    private static final String SYSTEM = "VISITOR_MANAGEMENT_SYSTEM";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final String SELECT_COLS =
            "system_code, use_case_code, frequency, generated_at, valid_from, valid_to, " +
                    "status, model_name, model_version, payload";

    public List<AnalyticsRecordDTO> findCurrent(String frequency) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + SELECT_COLS + " FROM ai_dashboard_current WHERE system_code = :system");
        MapSqlParameterSource p = new MapSqlParameterSource("system", SYSTEM);
        if (frequency != null) {
            sql.append(" AND frequency = :frequency");
            p.addValue("frequency", frequency);
        }
        sql.append(" ORDER BY use_case_code");
        return jdbc.query(sql.toString(), p, this::map);
    }

    public Optional<AnalyticsRecordDTO> findOneCurrent(String useCaseCode, String frequency) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + SELECT_COLS +
                        " FROM ai_dashboard_current WHERE system_code = :system AND use_case_code = :code");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("system", SYSTEM)
                .addValue("code", useCaseCode);
        if (frequency != null) {
            sql.append(" AND frequency = :frequency");
            p.addValue("frequency", frequency);
        }
        sql.append(" ORDER BY generated_at DESC LIMIT 1");
        return jdbc.query(sql.toString(), p, this::map).stream().findFirst();
    }

    public List<AnalyticsRecordDTO> findHistory(String useCaseCode, String frequency,
                                                Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + SELECT_COLS +
                        " FROM ai_dashboard_history WHERE system_code = :system AND use_case_code = :code");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("system", SYSTEM)
                .addValue("code", useCaseCode)
                .addValue("limit", limit);
        if (frequency != null) {
            sql.append(" AND frequency = :frequency");
            p.addValue("frequency", frequency);
        }
        if (from != null) {
            sql.append(" AND generated_at >= :fromTs");
            p.addValue("fromTs", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND generated_at <= :toTs");
            p.addValue("toTs", Timestamp.from(to));
        }
        sql.append(" ORDER BY generated_at DESC LIMIT :limit");
        return jdbc.query(sql.toString(), p, this::map);
    }

    public long countCurrent() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_dashboard_current WHERE system_code = :system",
                new MapSqlParameterSource("system", SYSTEM),
                Long.class);
        return n == null ? 0L : n;
    }

    public Optional<Instant> latestGeneratedAt() {
        List<Instant> rows = jdbc.query(
                "SELECT generated_at FROM ai_dashboard_current " +
                        "WHERE system_code = :system ORDER BY generated_at DESC LIMIT 1",
                new MapSqlParameterSource("system", SYSTEM),
                (rs, n) -> toInstant(rs.getTimestamp(1)));
        return rows.stream().findFirst();
    }

    // ---- mapping ----

    private AnalyticsRecordDTO map(ResultSet rs, int rowNum) throws SQLException {
        JsonNode payload = readJson(rs.getString("payload"));
        ModelInfoDTO model = new ModelInfoDTO(
                rs.getString("model_name"),
                rs.getString("model_version"),
                payload.path("modelMetrics")
        );
        return new AnalyticsRecordDTO(
                rs.getString("system_code"),
                rs.getString("use_case_code"),
                rs.getString("frequency"),
                toInstant(rs.getTimestamp("generated_at")),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_to")),
                rs.getString("status"),
                model,
                payload.path("summary"),
                payload.path("chart"),
                payload.path("alerts"),
                payload.path("details"),
                false // stale is computed in service
        );
    }

    private JsonNode readJson(String v) throws SQLException {
        try {
            return objectMapper.readTree(v == null ? "{}" : v);
        } catch (Exception e) {
            throw new SQLException("Invalid JSON in ai_dashboard payload", e);
        }
    }

    private Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}