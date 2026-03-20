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
            this._container.style.cssText = 'position: fixed; top: 24px; left: 0; right: 0; display: flex; flex-direction: column; align-items: center; z-index: 10000; pointer-events: none;';
            document.body.appendChild(this._container);
        },
        
        show(content, type = 'info', duration = 3000) {
            this._init();
            
            const messageEl = document.createElement('div');
            messageEl.className = `message ${type}`;
            messageEl.textContent = content;
            messageEl.style.cssText = `
                margin-bottom: 8px;
                padding: 12px 24px;
                border-radius: 4px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                animation: messageFadeIn 0.3s ease forwards;
                white-space: nowrap;
                pointer-events: auto;
            `;
            
            const colors = {
                success: 'background: #52c41a; color: #fff;',
                error: 'background: #f5222d; color: #fff;',
                warning: 'background: #faad14; color: #fff;',
                info: 'background: #3b82f6; color: #fff;'
            };
            
            messageEl.style.cssText += colors[type] || colors.info;
            
            this._container.appendChild(messageEl);
            
            setTimeout(() => {
                messageEl.style.animation = 'messageFadeOut 0.3s ease forwards';
                setTimeout(() => messageEl.remove(), 300);
            }, duration);
            
            return messageEl;
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

    const style = document.createElement('style');
    style.textContent = `
        @keyframes messageFadeIn {
            from { 
                opacity: 0; 
                transform: scale(0.8);
            }
            to { 
                opacity: 1; 
                transform: scale(1);
            }
        }
        @keyframes messageFadeOut {
            from { 
                opacity: 1; 
                transform: scale(1);
            }
            to { 
                opacity: 0; 
                transform: scale(0.8);
            }
        }
    `;
    document.head.appendChild(style);

    window.MessageUtil = messageUtil;
    window.message = messageUtil;
})();
