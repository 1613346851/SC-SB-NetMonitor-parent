/**
 * 网络监测系统 - 表格渲染组件
 * 提供统一的表格数据渲染和格式化
 */

(function() {
    const TableRenderer = {
        renderRiskLevel(level) {
            const config = AppConfig.RISK_LEVELS[level] || { text: level, class: 'info' };
            const riskClass = level === 'CRITICAL' ? 'risk-critical' : 
                             level === 'HIGH' ? 'risk-high' : 
                             level === 'MEDIUM' ? 'risk-medium' : 
                             level === 'LOW' ? 'risk-low' : '';
            const extraStyle = level === 'CRITICAL' ? 'animation: pulse-danger 2s infinite;' : '';
            return `<span class="tag ${config.class} ${riskClass}" style="${extraStyle}">${config.text}</span>`;
        },
        
        renderAttackType(type) {
            const config = AppConfig.ATTACK_TYPES[type] || { text: type || '未知', class: 'info' };
            return `<span class="tag ${config.class}">${config.text}</span>`;
        },
        
        renderDefenseType(type) {
            const config = AppConfig.DEFENSE_TYPES[type] || { text: type || '未知', class: 'info' };
            return `<span class="tag ${config.class}">${config.text}</span>`;
        },

        renderVerifyStatus(status) {
            const config = AppConfig.VERIFY_STATUS?.[status] || { text: status ?? '未知', class: 'info' };
            return `<span class="tag ${config.class}">${config.text}</span>`;
        },
        
        renderStatus(status, type = 'handle') {
            if (type === 'handle') {
                if (status === 1) {
                    const config = AppConfig.STATUS.HANDLED;
                    return `<span class="tag ${config.class}">${config.text}</span>`;
                } else if (status === 2) {
                    const config = AppConfig.STATUS.IGNORED;
                    return `<span class="tag ${config.class}">${config.text}</span>`;
                } else {
                    const config = AppConfig.STATUS.UNHANDLED;
                    return `<span class="tag ${config.class}">${config.text}</span>`;
                }
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
            if (text == null || text === '') return '-';
            if (text.length <= maxLength) return this.escapeHtml(text);
            return `<span title="${this.escapeHtml(text)}">${this.escapeHtml(text.substring(0, maxLength))}...</span>`;
        },
        
        renderIp(ip) {
            if (!ip) return '-';
            return `<span class="ip-address" style="font-family: monospace;">${this.escapeHtml(ip)}</span>`;
        },
        
        renderLink(text, url, newTab = false) {
            const target = newTab ? ' target="_blank"' : '';
            return `<a href="${url}"${target} style="color: #4f46e5; text-decoration: none;">${this.escapeHtml(text)}</a>`;
        },
        
        renderActions(actions) {
            return actions.map(action => {
                const { text, class: cls = 'btn-primary', onClick, disabled = false } = action;
                const disabledAttr = disabled ? ' disabled' : '';
                return `<button class="btn ${cls} btn-sm"${disabledAttr} onclick="${onClick}">${text}</button>`;
            }).join(' ');
        },
        
        renderActionsSmart(actions, options = {}) {
            const { maxVisible = 2, moreText = '更多' } = options;
            
            if (actions.length <= maxVisible) {
                return this.renderActions(actions);
            }
            
            const visibleActions = actions.slice(0, maxVisible);
            const dropdownActions = actions.slice(maxVisible);
            
            let html = this.renderActions(visibleActions);
            
            const dropdownId = 'dropdown_' + Math.random().toString(36).substr(2, 9);
            
            html += `
                <div class="action-dropdown" id="${dropdownId}">
                    <button class="action-dropdown-toggle" onclick="toggleActionDropdown('${dropdownId}', event)">
                        ${moreText}
                        <span class="dropdown-arrow">▼</span>
                    </button>
                    <div class="action-dropdown-menu">
                        ${dropdownActions.map(action => {
                            const { text, class: cls = '', onClick, disabled = false } = action;
                            const disabledAttr = disabled ? ' disabled' : '';
                            const typeClass = cls.includes('danger') ? 'danger' : 
                                             cls.includes('warning') ? 'warning' : 
                                             cls.includes('success') ? 'success' : '';
                            return `<button class="action-dropdown-item ${typeClass}"${disabledAttr} onclick="${onClick}">${text}</button>`;
                        }).join('')}
                    </div>
                </div>
            `;
            
            return html;
        },
        
        renderEmpty(colspan = 1, message = '暂无数据') {
            return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px; color: #999;">${message}</td></tr>`;
        },
        
        renderLoading(colspan = 1) {
            return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px;">
                <div class="spinner" style="width: 32px; height: 32px; border: 3px solid #e8e8e8; border-top-color: #4f46e5; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;"></div>
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
})();

function toggleActionDropdown(dropdownId, event) {
    event.stopPropagation();
    const dropdown = document.getElementById(dropdownId);
    if (!dropdown) return;
    
    const isOpen = dropdown.classList.contains('open');
    
    document.querySelectorAll('.action-dropdown.open').forEach(el => {
        el.classList.remove('open');
    });
    
    if (!isOpen) {
        dropdown.classList.add('open');
    }
}

document.addEventListener('click', function() {
    document.querySelectorAll('.action-dropdown.open').forEach(el => {
        el.classList.remove('open');
    });
});
