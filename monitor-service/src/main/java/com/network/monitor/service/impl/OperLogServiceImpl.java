package com.network.monitor.service.impl;

import com.network.monitor.entity.OperLogEntity;
import com.network.monitor.mapper.OperLogMapper;
import com.network.monitor.service.OperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperLogServiceImpl implements OperLogService {
    
    @Autowired
    private OperLogMapper operLogMapper;
    
    @Override
    public OperLogEntity getById(Long id) {
        return operLogMapper.selectById(id);
    }
    
    @Override
    public List<OperLogEntity> listLogs(String username, String operType, Integer operStatus, 
                                         String startTime, String endTime) {
        return operLogMapper.selectList(username, operType, operStatus, startTime, endTime);
    }
    
    @Override
    @Async
    public void log(String username, String operType, String operModule, String operContent,
                    String operMethod, String operUrl, String operIp, Integer operStatus, 
                    String errorMsg, Long costTime) {
        OperLogEntity log = new OperLogEntity();
        log.setUsername(username);
        log.setOperType(operType);
        log.setOperModule(operModule);
        log.setOperContent(operContent);
        log.setOperMethod(operMethod);
        log.setOperUrl(operUrl);
        log.setOperIp(operIp);
        log.setOperStatus(operStatus);
        log.setErrorMsg(errorMsg);
        log.setCostTime(costTime);
        
        operLogMapper.insert(log);
    }
    
    @Override
    @Async
    public void logLogin(String username, String operIp, Integer operStatus, String errorMsg) {
        log(username, "LOGIN", "系统登录", "用户登录", "login", "/auth/login", 
            operIp, operStatus, errorMsg, null);
    }
    
    @Override
    @Async
    public void logLogout(String username, String operIp) {
        log(username, "LOGOUT", "系统登出", "用户退出登录", "logout", "/auth/logout", 
            operIp, 0, null, null);
    }
    
    @Override
    @Async
    public void logOperation(String username, String operType, String operModule, String operContent,
                             String operMethod, String operUrl, String operIp, Integer operStatus) {
        log(username, operType, operModule, operContent, operMethod, operUrl, 
            operIp, operStatus, null, null);
    }
    
    @Override
    public boolean deleteLog(Long id) {
        return operLogMapper.deleteById(id) > 0;
    }
    
    @Override
    public int clearLogs(String beforeTime) {
        return operLogMapper.clearLogs(beforeTime);
    }
}
