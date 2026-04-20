package com.network.monitor.mapper;

import com.network.monitor.entity.OperLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperLogMapper {
    
    OperLogEntity selectById(@Param("id") Long id);
    
    List<OperLogEntity> selectList(@Param("username") String username,
                                    @Param("operType") String operType,
                                    @Param("operStatus") Integer operStatus,
                                    @Param("startTime") String startTime,
                                    @Param("endTime") String endTime);
    
    List<OperLogEntity> selectListWithPaging(@Param("username") String username,
                                              @Param("operType") String operType,
                                              @Param("operStatus") Integer operStatus,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("pageSize") int pageSize,
                                              @Param("sortField") String sortField,
                                              @Param("sortOrder") String sortOrder);
    
    long countList(@Param("username") String username,
                   @Param("operType") String operType,
                   @Param("operStatus") Integer operStatus,
                   @Param("startTime") String startTime,
                   @Param("endTime") String endTime);
    
    int insert(OperLogEntity entity);
    
    int deleteById(@Param("id") Long id);
    
    int clearLogs(@Param("beforeTime") String beforeTime);
}
