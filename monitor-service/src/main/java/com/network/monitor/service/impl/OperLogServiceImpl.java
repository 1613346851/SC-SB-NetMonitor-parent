package com.network.monitor.service.impl;

import com.network.monitor.entity.OperLogEntity;
import com.network.monitor.mapper.OperLogMapper;
import com.network.monitor.service.OperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<OperLogEntity> listAllLogsForExport(String username, String operType, Integer operStatus,
                                                     String startTime, String endTime) {
        List<OperLogEntity> allLogs = new ArrayList<>();
        int pageSize = 1000;
        int pageNum = 1;
        long total;
        
        do {
            int offset = (pageNum - 1) * pageSize;
            List<OperLogEntity> page = operLogMapper.selectListWithPaging(
                username, operType, operStatus, startTime, endTime, offset, pageSize, "oper_time", "desc"
            );
            allLogs.addAll(page);
            total = operLogMapper.countList(username, operType, operStatus, startTime, endTime);
            pageNum++;
        } while ((long) (pageNum - 1) * pageSize < total);
        
        return allLogs;
    }
    
    @Override
    public Map<String, Object> listLogsWithPaging(String username, String operType, Integer operStatus,
                                                   String startTime, String endTime, int pageNum, int pageSize,
                                                   String sortField, String sortOrder) {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 10;
        }
        
        String dbSortField = convertToDbColumn(sortField);
        if (dbSortField == null) {
            dbSortField = "oper_time";
        }
        if (!"asc".equalsIgnoreCase(sortOrder) && !"desc".equalsIgnoreCase(sortOrder)) {
            sortOrder = "desc";
        }
        
        int offset = (pageNum - 1) * pageSize;
        
        List<OperLogEntity> list = operLogMapper.selectListWithPaging(
            username, operType, operStatus, startTime, endTime, offset, pageSize, dbSortField, sortOrder
        );
        
        long total = operLogMapper.countList(username, operType, operStatus, startTime, endTime);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        
        return result;
    }
    
    private String convertToDbColumn(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("id", "id");
        fieldMap.put("operName", "username");
        fieldMap.put("username", "username");
        fieldMap.put("operType", "oper_type");
        fieldMap.put("module", "oper_module");
        fieldMap.put("operModule", "oper_module");
        fieldMap.put("operIp", "oper_ip");
        fieldMap.put("status", "oper_status");
        fieldMap.put("operStatus", "oper_status");
        fieldMap.put("operTime", "oper_time");
        fieldMap.put("createTime", "oper_time");
        return fieldMap.get(field);
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
