/**
 * 网络监测系统 - 消息提示工具
 * 提供统一的消息提示接口
 */

(function() {
    const messageUtil = {
        _container: null,
        
        _init() {
            if (this._container) return;
            
            this._container = document.createElement('div');
            this._container.className = 'message-container';
            document.body.appendChild(this._container);
        },
        
        show(content, type = 'info', duration = 3000) {
            this._init();
            
            const messageEl = document.createElement('div');
            messageEl.className = `message ${type}`;
            
            const iconMap = {
                success: '✓',
                error: '✕',
                warning: '⚠',
                info: 'ℹ'
            };
            
            messageEl.innerHTML = `
                <span class="message-icon">${iconMap[type] || iconMap.info}</span>
                <span class="message-content">${this._escapeHtml(content)}</span>
            `;
            
            this._container.appendChild(messageEl);
            
            setTimeout(() => {
                messageEl.classList.add('hide');
                setTimeout(() => messageEl.remove(), 300);
            }, duration);
            
            return messageEl;
        },
        
        _escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        },
        
        success(content, duration) {
            return this.show(content, 'success', duration);
        },
        
        error(content, duration) {
            return this.show(content, 'error', duration);
        },
        
        warning(content, duration) {
            return this.show(content, 'warning', duration);
        },
        
        info(content, duration) {
            return this.show(content, 'info', duration);
        }
    };

    window.MessageUtil = messageUtil;
    window.message = messageUtil;
})();
