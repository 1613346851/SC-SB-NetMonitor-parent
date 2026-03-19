/**
 * 网络监测系统 - 日期工具
 * 提供日期格式化、解析、计算等功能
 */

const DateUtil = {
    format(dateTime, pattern = AppConfig.DATE_FORMAT.DEFAULT) {
        if (!dateTime) return '';
        
        const date = dateTime instanceof Date ? dateTime : new Date(dateTime);
        
        if (isNaN(date.getTime())) return '';
        
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
    
    now() {
        return this.format(new Date());
    },
    
    today() {
        return this.format(new Date(), AppConfig.DATE_FORMAT.DATE_ONLY);
    },
    
    daysAgo(days) {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return this.format(date, AppConfig.DATE_FORMAT.DATE_ONLY);
    },
    
    hoursAgo(hours) {
        const date = new Date();
        date.setHours(date.getHours() - hours);
        return this.format(date);
    },
    
    parse(dateStr) {
        if (!dateStr) return null;
        
        const parsed = new Date(dateStr.replace(' ', 'T'));
        return isNaN(parsed.getTime()) ? null : parsed;
    },
    
    formatForChart(date) {
        const d = date instanceof Date ? date : new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    },
    
    formatDuration(seconds) {
        if (!seconds || seconds <= 0) return '0秒';
        
        const days = Math.floor(seconds / 86400);
        const hours = Math.floor((seconds % 86400) / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = seconds % 60;
        
        const parts = [];
        if (days > 0) parts.push(`${days}天`);
        if (hours > 0) parts.push(`${hours}小时`);
        if (minutes > 0) parts.push(`${minutes}分`);
        if (secs > 0 || parts.length === 0) parts.push(`${secs}秒`);
        
        return parts.join('');
    },
    
    getRelativeTime(dateTime) {
        const date = dateTime instanceof Date ? dateTime : new Date(dateTime);
        const now = new Date();
        const diff = now - date;
        
        const seconds = Math.floor(diff / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        const days = Math.floor(hours / 24);
        
        if (days > 0) return `${days}天前`;
        if (hours > 0) return `${hours}小时前`;
        if (minutes > 0) return `${minutes}分钟前`;
        if (seconds > 0) return `${seconds}秒前`;
        return '刚刚';
    }
};

window.DateUtil = DateUtil;
