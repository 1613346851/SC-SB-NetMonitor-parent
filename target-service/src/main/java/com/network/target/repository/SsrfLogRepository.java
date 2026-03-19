package com.network.target.repository;

import com.network.target.entity.SsrfLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SsrfLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public SsrfLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SsrfLogEntity> rowMapper = (rs, rowNum) -> {
        SsrfLogEntity entity = new SsrfLogEntity();
        entity.setId(rs.getInt("id"));
        entity.setRequestUrl(rs.getString("request_url"));
        entity.setRequestMethod(rs.getString("request_method"));
        entity.setResponseCode(rs.getInt("response_code"));
        entity.setResponseBody(rs.getString("response_body"));
        entity.setRequestTime(rs.getTimestamp("request_time").toLocalDateTime());
        entity.setSourceIp(rs.getString("source_ip"));
        return entity;
    };

    public int save(SsrfLogEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_ssrf_log (request_url, request_method, response_code, response_body, source_ip) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, entity.getRequestUrl());
            ps.setString(2, entity.getRequestMethod());
            ps.setInt(3, entity.getResponseCode() != null ? entity.getResponseCode() : 0);
            ps.setString(4, entity.getResponseBody());
            ps.setString(5, entity.getSourceIp());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().intValue() : 0;
    }

    public List<SsrfLogEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM sys_ssrf_log ORDER BY request_time DESC LIMIT 100", rowMapper);
    }

    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM sys_ssrf_log");
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_ssrf_log", Long.class);
        return count != null ? count : 0;
    }
}
