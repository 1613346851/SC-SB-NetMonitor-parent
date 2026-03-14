package com.network.monitor.mapper;

import com.network.monitor.entity.IpBlacklistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IpBlacklistMapper {

    int insert(IpBlacklistEntity entity);

    IpBlacklistEntity selectById(@Param("id") Long id);

    IpBlacklistEntity selectByIpAddress(@Param("ipAddress") String ipAddress);

    List<IpBlacklistEntity> selectAll();

    List<IpBlacklistEntity> selectBanning();

    List<IpBlacklistEntity> selectExpired();

    int updateById(IpBlacklistEntity entity);

    int updateStatusByIpAddress(@Param("ipAddress") String ipAddress, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);

    int deleteById(@Param("id") Long id);

    int deleteByIpAddress(@Param("ipAddress") String ipAddress);

    long countAll();

    long countBanning();
}
