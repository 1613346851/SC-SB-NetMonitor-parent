package com.network.monitor.entity;

import java.time.LocalDateTime;

public class OperLogEntity {
    
    private Long id;
    private String username;
    private String operType;
    private String operModule;
    private String operContent;
    private String operMethod;
    private String operUrl;
    private String operIp;
    private String operLocation;
    private Integer operStatus;
    private String errorMsg;
    private LocalDateTime operTime;
    private Long costTime;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getOperType() {
        return operType;
    }
    
    public void setOperType(String operType) {
        this.operType = operType;
    }
    
    public String getOperModule() {
        return operModule;
    }
    
    public void setOperModule(String operModule) {
        this.operModule = operModule;
    }
    
    public String getOperContent() {
        return operContent;
    }
    
    public void setOperContent(String operContent) {
        this.operContent = operContent;
    }
    
    public String getOperMethod() {
        return operMethod;
    }
    
    public void setOperMethod(String operMethod) {
        this.operMethod = operMethod;
    }
    
    public String getOperUrl() {
        return operUrl;
    }
    
    public void setOperUrl(String operUrl) {
        this.operUrl = operUrl;
    }
    
    public String getOperIp() {
        return operIp;
    }
    
    public void setOperIp(String operIp) {
        this.operIp = operIp;
    }
    
    public String getOperLocation() {
        return operLocation;
    }
    
    public void setOperLocation(String operLocation) {
        this.operLocation = operLocation;
    }
    
    public Integer getOperStatus() {
        return operStatus;
    }
    
    public void setOperStatus(Integer operStatus) {
        this.operStatus = operStatus;
    }
    
    public String getErrorMsg() {
        return errorMsg;
    }
    
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
    
    public LocalDateTime getOperTime() {
        return operTime;
    }
    
    public void setOperTime(LocalDateTime operTime) {
        this.operTime = operTime;
    }
    
    public Long getCostTime() {
        return costTime;
    }
    
    public void setCostTime(Long costTime) {
        this.costTime = costTime;
    }
}
