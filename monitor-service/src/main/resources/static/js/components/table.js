/**
 * 网络监测系统 - 表格渲染组件
 * 提供统一的表格数据渲染和格式化
 */

const TableRenderer = {
    renderRiskLevel(level) {
        const config = AppConfig.RISK_LEVELS[level] || { text: level, class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },
    
    renderAttackType(type) {
        const config = AppConfig.ATTACK_TYPES[type] || { text: type || '未知', class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },
    
    renderDefenseType(type) {
        const config = AppConfig.DEFENSE_TYPES[type] || { text: type || '未知', class: 'info' };
        return `<span class="tag ${config.class}">${config.text}</span>`;
    },
    
    renderStatus(status, type = 'handle') {
        if (type === 'handle') {
            const config = status === 1 ? AppConfig.STATUS.HANDLED : AppConfig.STATUS.UNHANDLED;
            return `<span class="tag ${config.class}">${config.text}</span>`;
        } else if (type === 'ban') {
            if (status === 1) {
                return `<span class="tag ${AppConfig.STATUS.BANNING.class}">${AppConfig.STATUS.BANNING.text}</span>`;
            } else {
                return `<span class="tag ${AppConfig.STATUS.EXPIRED.class}">${AppConfig.STATUS.EXPIRED.text}</span>`;
            }
        }
        return `<span class="tag info">${status}</span>`;
    },
    
    renderBoolean(value, trueText = '是', falseText = '否') {
        return value ? 
            `<span class="tag success">${trueText}</span>` : 
            `<span class="tag danger">${falseText}</span>`;
    },
    
    renderDateTime(dateTime, pattern = AppConfig.DATE_FORMAT.DEFAULT) {
        return DateUtil.format(dateTime, pattern) || '-';
    },
    
    renderText(text, maxLength = 50) {
        if (!text) return '-';
        if (text.length <= maxLength) return this.escapeHtml(text);
        return `<span title="${this.escapeHtml(text)}">${this.escapeHtml(text.substring(0, maxLength))}...</span>`;
    },
    
    renderIp(ip) {
        if (!ip) return '-';
        return `<span class="ip-address" style="font-family: monospace;">${this.escapeHtml(ip)}</span>`;
    },
    
    renderLink(text, url, newTab = false) {
        const target = newTab ? ' target="_blank"' : '';
        return `<a href="${url}"${target} style="color: #1890ff; text-decoration: none;">${this.escapeHtml(text)}</a>`;
    },
    
    renderActions(actions) {
        return actions.map(action => {
            const { text, class: cls = 'btn-primary', onClick, disabled = false } = action;
            const disabledAttr = disabled ? ' disabled' : '';
            return `<button class="btn ${cls} btn-sm"${disabledAttr} onclick="${onClick}">${text}</button>`;
        }).join(' ');
    },
    
    renderEmpty(colspan = 1, message = '暂无数据') {
        return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px; color: #999;">${message}</td></tr>`;
    },
    
    renderLoading(colspan = 1) {
        return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px;">
            <div class="spinner" style="width: 32px; height: 32px; border: 3px solid #e8e8e8; border-top-color: #1890ff; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;"></div>
            <div style="margin-top: 12px; color: #999;">加载中...</div>
        </td></tr>`;
    },
    
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

window.TableRenderer = TableRenderer;
window.tableRenderer = TableRenderer;
