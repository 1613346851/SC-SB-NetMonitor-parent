package com.network.monitor.mapper;

import com.network.monitor.entity.IpBlacklistHistoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IpBlacklistHistoryMapper {

    int insert(IpBlacklistHistoryEntity entity);

    IpBlacklistHistoryEntity selectById(@Param("id") Long id);

    List<IpBlacklistHistoryEntity> selectByBlacklistId(@Param("blacklistId") Long blacklistId);

    List<IpBlacklistHistoryEntity> selectBanningByBlacklistId(@Param("blacklistId") Long blacklistId);

    List<IpBlacklistHistoryEntity> selectAll();

    int updateById(IpBlacklistHistoryEntity entity);

    int updateProcessStatusById(@Param("id") Long id, @Param("processStatus") Integer processStatus, @Param("unbanReason") String unbanReason);

    int deleteById(@Param("id") Long id);

    int deleteByBlacklistId(@Param("blacklistId") Long blacklistId);

    long countByBlacklistId(@Param("blacklistId") Long blacklistId);

    int countByIp(@Param("ip") String ip);
}
