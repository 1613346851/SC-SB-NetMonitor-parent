package com.network.monitor.service;

import com.network.monitor.entity.OperLogEntity;

import java.util.List;

public interface OperLogService {
    
    OperLogEntity getById(Long id);
    
    List<OperLogEntity> listLogs(String username, String operType, Integer operStatus, 
                                  String startTime, String endTime);
    
    void log(String username, String operType, String operModule, String operContent,
             String operMethod, String operUrl, String operIp, Integer operStatus, String errorMsg, Long costTime);
    
    void logLogin(String username, String operIp, Integer operStatus, String errorMsg);
    
    void logLogout(String username, String operIp);
    
    void logOperation(String username, String operType, String operModule, String operContent,
                      String operMethod, String operUrl, String operIp, Integer operStatus);
    
    boolean deleteLog(Long id);
    
    int clearLogs(String beforeTime);
}
