package com.network.monitor.mapper;

import com.network.monitor.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper {

    int insert(SysConfigEntity entity);

    SysConfigEntity selectById(@Param("id") Long id);

    SysConfigEntity selectByKey(@Param("configKey") String configKey);

    List<SysConfigEntity> selectAll();

    int updateById(SysConfigEntity entity);

    int updateByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);

    int deleteById(@Param("id") Long id);

    List<String> selectAllKeys();
}