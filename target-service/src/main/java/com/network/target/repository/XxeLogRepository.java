package com.network.target.repository;

import com.network.target.entity.XxeLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class XxeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public XxeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<XxeLogEntity> rowMapper = (rs, rowNum) -> {
        XxeLogEntity entity = new XxeLogEntity();
        entity.setId(rs.getInt("id"));
        entity.setXmlContent(rs.getString("xml_content"));
        entity.setParseResult(rs.getString("parse_result"));
        entity.setHasExternalEntity(rs.getBoolean("has_external_entity"));
        entity.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        return entity;
    };

    public int save(XxeLogEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_xxe_log (xml_content, parse_result, has_external_entity) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, entity.getXmlContent());
            ps.setString(2, entity.getParseResult());
            ps.setBoolean(3, entity.getHasExternalEntity() != null ? entity.getHasExternalEntity() : false);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().intValue() : 0;
    }

    public List<XxeLogEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM sys_xxe_log ORDER BY create_time DESC LIMIT 100", rowMapper);
    }

    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM sys_xxe_log");
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_xxe_log", Long.class);
        return count != null ? count : 0;
    }
}
