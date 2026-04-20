package com.network.monitor.mapper;

import com.network.monitor.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {
    
    UserEntity selectByUsername(@Param("username") String username);
    
    UserEntity selectById(@Param("id") Long id);
    
    List<UserEntity> selectList(@Param("username") String username,
                                 @Param("status") Integer status,
                                 @Param("delFlag") Integer delFlag);
    
    List<UserEntity> selectListPaged(@Param("username") String username,
                                      @Param("status") Integer status,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);
    
    long countList(@Param("username") String username,
                   @Param("status") Integer status);
    
    int insert(UserEntity entity);
    
    int update(UserEntity entity);
    
    int deleteById(@Param("id") Long id);
    
    int updateLoginInfo(@Param("id") Long id,
                        @Param("lastLoginTime") LocalDateTime lastLoginTime,
                        @Param("lastLoginIp") String lastLoginIp);
    
    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("passwordUpdateTime") LocalDateTime passwordUpdateTime);
    
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    
    int updateLoginFailCount(@Param("id") Long id, @Param("loginFailCount") Integer loginFailCount);
    
    int resetLoginFailCount(@Param("id") Long id);
    
    int countByUsername(@Param("username") String username);
}
