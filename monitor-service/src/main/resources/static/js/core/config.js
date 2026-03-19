/**
 * 网络监测系统 - 核心配置
 * 集中管理所有配置常量
 */

const AppConfig = {
    API_BASE_URL: '/api',
    
    AUTH: {
        TOKEN_KEY: 'auth_token',
        USER_KEY: 'user_info',
        LOGIN_URL: '/login',
        TOKEN_HEADER: 'Authorization',
        TOKEN_PREFIX: 'Bearer '
    },
    
    PAGINATION: {
        DEFAULT_PAGE_SIZE: 10,
        PAGE_SIZE_OPTIONS: [10, 20, 50, 100]
    },
    
    STORAGE: {
        PREFIX: 'nm_'
    },
    
    DATE_FORMAT: {
        DEFAULT: 'YYYY-MM-DD HH:mm:ss',
        DATE_ONLY: 'YYYY-MM-DD',
        TIME_ONLY: 'HH:mm:ss',
        DISPLAY: 'MM-DD HH:mm'
    },
    
    RISK_LEVELS: {
        HIGH: { text: '高风险', class: 'danger', color: '#f5222d' },
        MEDIUM: { text: '中风险', class: 'warning', color: '#faad14' },
        LOW: { text: '低风险', class: 'info', color: '#1890ff' },
        CRITICAL: { text: '严重', class: 'danger', color: '#a8071a' }
    },
    
    ATTACK_TYPES: {
        SQL_INJECTION: { text: 'SQL 注入', class: 'danger' },
        XSS: { text: 'XSS 攻击', class: 'warning' },
        COMMAND_INJECTION: { text: '命令注入', class: 'danger' },
        DDOS: { text: 'DDoS 攻击', class: 'danger' },
        PATH_TRAVERSAL: { text: '路径遍历', class: 'warning' },
        FILE_INCLUSION: { text: '文件包含', class: 'warning' },
        BRUTE_FORCE: { text: '暴力破解', class: 'warning' }
    },
    
    DEFENSE_TYPES: {
        BLOCK_IP: { text: '封禁IP', class: 'danger' },
        RATE_LIMIT: { text: '限流', class: 'warning' },
        BLOCK_REQUEST: { text: '拦截请求', class: 'info' }
    },
    
    STATUS: {
        HANDLED: { text: '已处理', class: 'success', value: 1 },
        UNHANDLED: { text: '未处理', class: 'warning', value: 0 },
        BANNING: { text: '封禁中', class: 'danger', value: 1 },
        EXPIRED: { text: '已过期', class: 'info', value: 2 }
    }
};

Object.freeze(AppConfig);
Object.freeze(AppConfig.AUTH);
Object.freeze(AppConfig.PAGINATION);
Object.freeze(AppConfig.STORAGE);
Object.freeze(AppConfig.DATE_FORMAT);
Object.freeze(AppConfig.RISK_LEVELS);
Object.freeze(AppConfig.ATTACK_TYPES);
Object.freeze(AppConfig.DEFENSE_TYPES);
Object.freeze(AppConfig.STATUS);

window.AppConfig = AppConfig;
