package com.network.target.repository;

import com.network.target.entity.SerializedObjectEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SerializedObjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public SerializedObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SerializedObjectEntity> rowMapper = (rs, rowNum) -> {
        SerializedObjectEntity entity = new SerializedObjectEntity();
        entity.setId(rs.getInt("id"));
        entity.setObjectName(rs.getString("object_name"));
        entity.setObjectType(rs.getString("object_type"));
        entity.setSerializedData(rs.getBytes("serialized_data"));
        entity.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        return entity;
    };

    public int save(SerializedObjectEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_serialized_object (object_name, object_type, serialized_data) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, entity.getObjectName());
            ps.setString(2, entity.getObjectType());
            ps.setBytes(3, entity.getSerializedData());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().intValue() : 0;
    }

    public List<SerializedObjectEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM sys_serialized_object ORDER BY create_time DESC", rowMapper);
    }

    public SerializedObjectEntity findById(Integer id) {
        List<SerializedObjectEntity> results = jdbcTemplate.query(
                "SELECT * FROM sys_serialized_object WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public int deleteById(Integer id) {
        return jdbcTemplate.update("DELETE FROM sys_serialized_object WHERE id = ?", id);
    }

    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM sys_serialized_object");
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_serialized_object", Long.class);
        return count != null ? count : 0;
    }
}
