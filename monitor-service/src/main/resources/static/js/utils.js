/**
 * 网络监测系统 - 公共 JavaScript 工具
 * 提供 API 请求、消息提示、日期格式化等通用功能
 */

// ==================== API 基础配置 ====================
const API_BASE_URL = '/api';

// ==================== HTTP 请求封装 ====================
const http = {
    /**
     * GET 请求
     */
    async get(url, params = {}) {
        const queryString = new URLSearchParams(params).toString();
        const fullUrl = queryString ? `${API_BASE_URL}${url}?${queryString}` : `${API_BASE_URL}${url}`;
        
        try {
            const response = await fetch(fullUrl, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });
            
            const data = await response.json();
            
            if (response.ok && data.code === 200) {
                return data.data;
            } else {
                throw new Error(data.message || '请求失败');
            }
        } catch (error) {
            console.error('GET request error:', error);
            message.error(error.message);
            throw error;
        }
    },

    /**
     * POST 请求
     */
    async post(url, data = {}) {
        try {
            const response = await fetch(`${API_BASE_URL}${url}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(data),
            });
            
            const result = await response.json();
            
            if (response.ok && result.code === 200) {
                return result.data;
            } else {
                throw new Error(result.message || '请求失败');
            }
        } catch (error) {
            console.error('POST request error:', error);
            message.error(error.message);
            throw error;
        }
    },

    /**
     * PUT 请求
     */
    async put(url, data = {}) {
        try {
            const response = await fetch(`${API_BASE_URL}${url}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(data),
            });
            
            const result = await response.json();
            
            if (response.ok && result.code === 200) {
                return result.data;
            } else {
                throw new Error(result.message || '请求失败');
            }
        } catch (error) {
            console.error('PUT request error:', error);
            message.error(error.message);
            throw error;
        }
    },

    /**
     * DELETE 请求
     */
    async delete(url, params = {}) {
        const queryString = new URLSearchParams(params).toString();
        const fullUrl = queryString ? `${API_BASE_URL}${url}?${queryString}` : `${API_BASE_URL}${url}`;
        
        try {
            const response = await fetch(fullUrl, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                },
            });
            
            const result = await response.json();
            
            if (response.ok && result.code === 200) {
                return result.data;
            } else {
                throw new Error(result.message || '请求失败');
            }
        } catch (error) {
            console.error('DELETE request error:', error);
            message.error(error.message);
            throw error;
        }
    },
};

// ==================== 消息提示 ====================
const message = {
    success(content, duration = 3000) {
        this.show(content, 'success', duration);
    },

    error(content, duration = 3000) {
        this.show(content, 'error', duration);
    },

    warning(content, duration = 3000) {
        this.show(content, 'warning', duration);
    },

    show(content, type, duration) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;
        messageDiv.textContent = content;
        
        document.body.appendChild(messageDiv);
        
        setTimeout(() => {
            messageDiv.remove();
        }, duration);
    },
};

// ==================== 日期格式化工具 ====================
const dateFormat = {
    /**
     * 格式化日期时间
     */
    format(dateTime, pattern = 'YYYY-MM-DD HH:mm:ss') {
        if (!dateTime) return '';
        
        const date = new Date(dateTime);
        
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        
        return pattern
            .replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes)
            .replace('ss', seconds);
    },

    /**
     * 获取当前日期时间字符串
     */
    now() {
        return this.format(new Date());
    },

    /**
     * 获取 N 天前的日期
     */
    daysAgo(days) {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return this.format(date, 'YYYY-MM-DD');
    },
};

// ==================== 表格渲染工具 ====================
const tableRenderer = {
    /**
     * 渲染风险等级标签
     */
    renderRiskLevel(level) {
        const levelMap = {
            'HIGH': { text: '高风险', class: 'danger' },
            'MEDIUM': { text: '中风险', class: 'warning' },
            'LOW': { text: '低风险', class: 'info' },
        };
        
        const config = levelMap[level] || { text: level, class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },

    /**
     * 渲染处理状态标签
     */
    renderStatus(status) {
        const statusMap = {
            1: { text: '已处理', class: 'success' },
            0: { text: '未处理', class: 'warning' },
        };
        
        const config = statusMap[status] || { text: '未知', class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },

    /**
     * 渲染攻击类型标签
     */
    renderAttackType(type) {
        const typeMap = {
            'SQL_INJECTION': { text: 'SQL 注入', class: 'danger' },
            'XSS': { text: 'XSS 攻击', class: 'warning' },
            'COMMAND_INJECTION': { text: '命令注入', class: 'danger' },
            'DDOS': { text: 'DDoS 攻击', class: 'danger' },
            'PATH_TRAVERSAL': { text: '路径遍历', class: 'warning' },
            'FILE_INCLUSION': { text: '文件包含', class: 'warning' },
        };
        
        const config = typeMap[type] || { text: type, class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },
};

// ==================== 模态框工具 ====================
const modal = {
    /**
     * 打开模态框
     */
    open(content) {
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.innerHTML = content;
        
        document.body.appendChild(overlay);
        
        // 点击遮罩关闭
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                this.close(overlay);
            }
        });
        
        return overlay;
    },

    /**
     * 关闭模态框
     */
    close(overlay) {
        if (overlay) {
            overlay.remove();
        }
    },
};

// ==================== 表格分页工具 ====================
const pagination = {
    currentPage: 1,
    pageSize: 10,
    total: 0,

    /**
     * 初始化分页
     */
    init(container, options = {}) {
        this.currentPage = options.currentPage || 1;
        this.pageSize = options.pageSize || 10;
        this.total = options.total || 0;
        this.onChange = options.onChange;
        
        this.render(container);
    },

    /**
     * 渲染分页
     */
    render(container) {
        const totalPages = Math.ceil(this.total / this.pageSize);
        
        let html = `
            <div class="pagination">
                <span class="pagination-item ${this.currentPage === 1 ? 'disabled' : ''}" 
                      onclick="pagination.goPage(${this.currentPage - 1})">上一页</span>
        `;
        
        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= this.currentPage - 1 && i <= this.currentPage + 1)) {
                html += `
                    <span class="pagination-item ${i === this.currentPage ? 'active' : ''}" 
                          onclick="pagination.goPage(${i})">${i}</span>
                `;
            } else if (i === this.currentPage - 2 || i === this.currentPage + 2) {
                html += `<span class="pagination-item">...</span>`;
            }
        }
        
        html += `
            <span class="pagination-item ${this.currentPage === totalPages ? 'disabled' : ''}" 
                  onclick="pagination.goPage(${this.currentPage + 1})">下一页</span>
            <span class="pagination-item">共 ${this.total} 条</span>
        </div>
        `;
        
        container.innerHTML = html;
    },

    /**
     * 跳转页面
     */
    goPage(page) {
        const totalPages = Math.ceil(this.total / this.pageSize);
        
        if (page < 1 || page > totalPages || page === this.currentPage) {
            return;
        }
        
        this.currentPage = page;
        this.render(document.querySelector('.pagination-container'));
        
        if (this.onChange) {
            this.onChange(page, this.pageSize);
        }
    },
};

// ==================== 图表工具（ECharts 封装） ====================
const chartHelper = {
    /**
     * 初始化图表
     */
    init(domId) {
        if (typeof echarts !== 'undefined') {
            return echarts.init(document.getElementById(domId));
        }
        console.error('ECharts not loaded');
        return null;
    },

    /**
     * 配置项通用设置
     */
    getOption(baseOption = {}) {
        const defaultOption = {
            tooltip: {
                trigger: 'axis',
                axisPointer: {
                    type: 'shadow'
                }
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                containLabel: true
            },
            textStyle: {
                fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
            }
        };
        
        return { ...defaultOption, ...baseOption };
    },
};

// ==================== 表单验证工具 ====================
const validator = {
    /**
     * 验证是否为空
     */
    required(value, fieldName = '字段') {
        if (!value || (typeof value === 'string' && !value.trim())) {
            message.error(`${fieldName}不能为空`);
            return false;
        }
        return true;
    },

    /**
     * 验证邮箱格式
     */
    email(value) {
        const reg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!reg.test(value)) {
            message.error('邮箱格式不正确');
            return false;
        }
        return true;
    },

    /**
     * 验证手机号格式
     */
    phone(value) {
        const reg = /^1[3-9]\d{9}$/;
        if (!reg.test(value)) {
            message.error('手机号格式不正确');
            return false;
        }
        return true;
    },

    /**
     * 验证 IP 地址格式
     */
    ip(value) {
        const reg = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/;
        if (!reg.test(value)) {
            message.error('IP 地址格式不正确');
            return false;
        }
        return true;
    },
};

// ==================== 本地存储工具 ====================
const storage = {
    /**
     * 设置本地存储
     */
    set(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (error) {
            console.error('LocalStorage set error:', error);
        }
    },

    /**
     * 获取本地存储
     */
    get(key, defaultValue = null) {
        try {
            const value = localStorage.getItem(key);
            return value ? JSON.parse(value) : defaultValue;
        } catch (error) {
            console.error('LocalStorage get error:', error);
            return defaultValue;
        }
    },

    /**
     * 移除本地存储
     */
    remove(key) {
        try {
            localStorage.removeItem(key);
        } catch (error) {
            console.error('LocalStorage remove error:', error);
        }
    },

    /**
     * 清空本地存储
     */
    clear() {
        try {
            localStorage.clear();
        } catch (error) {
            console.error('LocalStorage clear error:', error);
        }
    },
};

// ==================== 导出所有工具 ====================
window.utils = {
    http,
    message,
    dateFormat,
    tableRenderer,
    modal,
    pagination,
    chartHelper,
    validator,
    storage,
};
