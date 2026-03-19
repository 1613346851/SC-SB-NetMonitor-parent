/**
 * 网络监测系统 - 公共工具入口
 * 统一导出所有工具模块，保持向后兼容
 */

(function() {
    'use strict';
    
    var modules = {
        http: window.HttpClient || null,
        message: window.MessageUtil || null,
        date: window.DateUtil || null,
        table: window.TableRenderer || null,
        modal: window.ModalUtil || null,
        pagination: window.PaginationUtil || null,
        chart: window.ChartUtil || null,
        validator: window.ValidatorUtil || null,
        storage: window.StorageUtil || null,
        auth: window.AuthService || null
    };
    
    window.utils = modules;
    
    if (modules.http) {
        window.http = modules.http;
        window.httpGet = function(url, params) { return modules.http.get(url, params); };
        window.httpPost = function(url, data) { return modules.http.post(url, data); };
    }
    
    if (modules.message) {
        window.message = modules.message;
        window.showSuccess = function(msg) { modules.message.success(msg); };
        window.showError = function(msg) { modules.message.error(msg); };
        window.showWarning = function(msg) { modules.message.warning(msg); };
        window.showInfo = function(msg) { modules.message.info(msg); };
    }
    
    if (modules.date) {
        window.DateUtil = modules.date;
        window.dateFormat = modules.date;
        window.formatDate = function(date, pattern) { return modules.date.format(date, pattern); };
        window.formatDateTime = function(date) { return modules.date.format(date); };
    }
    
    if (modules.table) {
        window.TableRenderer = modules.table;
        window.tableRenderer = modules.table;
    }
    
    if (modules.modal) {
        window.ModalUtil = modules.modal;
    }
    
    if (modules.pagination) {
        window.PaginationUtil = modules.pagination;
    }
    
    if (modules.chart) {
        window.ChartUtil = modules.chart;
        window.chartHelper = modules.chart;
    }
    
    if (modules.validator) {
        window.ValidatorUtil = modules.validator;
    }
    
    if (modules.storage) {
        window.StorageUtil = modules.storage;
    }
    
    if (modules.auth) {
        window.AuthService = modules.auth;
    }
    
    console.log('Network Monitor Utils loaded successfully');
})();
