(function() {
    const AlertNotification = {
        _container: null,
        _alerts: [],
        _socket: null,
        _reconnectAttempts: 0,
        _maxReconnectAttempts: 5,
        _reconnectDelay: 3000,
        _storageKey: 'pending_alerts',

        init() {
            this._createContainer();
            this._createStyles();
            this._loadPendingAlerts();
            this._connectWebSocket();
        },

        _createContainer() {
            this._container = document.createElement('div');
            this._container.id = 'alert-notification-container';
            this._container.style.cssText = `
                position: fixed;
                top: 80px;
                right: 20px;
                width: 380px;
                max-height: calc(100vh - 100px);
                overflow-y: auto;
                overflow-x: hidden;
                z-index: 99999;
                pointer-events: none;
                display: flex;
                flex-direction: column;
                gap: 12px;
                padding-right: 4px;
            `;
            document.body.appendChild(this._container);
        },

        _createStyles() {
            const style = document.createElement('style');
            style.textContent = `
                @keyframes alertSlideIn {
                    from {
                        opacity: 0;
                        transform: translateX(100%);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(0);
                    }
                }
                
                @keyframes alertSlideOut {
                    from {
                        opacity: 1;
                        transform: translateX(0);
                    }
                    to {
                        opacity: 0;
                        transform: translateX(100%);
                    }
                }
                
                .alert-notification-card {
                    pointer-events: auto;
                    border-radius: 12px;
                    padding: 16px;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
                    animation: alertSlideIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                    position: relative;
                    overflow: hidden;
                    min-height: 180px;
                    flex-shrink: 0;
                }
                
                .alert-notification-card.removing {
                    animation: alertSlideOut 0.3s ease forwards;
                }
                
                .alert-notification-card::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    opacity: 0.15;
                    pointer-events: none;
                }
                
                .alert-critical {
                    background: linear-gradient(135deg, #dc2626 0%, #991b1b 100%);
                    color: #fff;
                }
                
                .alert-critical::before {
                    background: linear-gradient(135deg, #fff 0%, transparent 50%);
                }
                
                .alert-high {
                    background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%);
                    color: #fff;
                }
                
                .alert-high::before {
                    background: linear-gradient(135deg, #fff 0%, transparent 50%);
                }
                
                .alert-medium {
                    background: linear-gradient(135deg, #d97706 0%, #b45309 100%);
                    color: #fff;
                }
                
                .alert-medium::before {
                    background: linear-gradient(135deg, #fff 0%, transparent 50%);
                }
                
                .alert-low {
                    background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
                    color: #fff;
                }
                
                .alert-low::before {
                    background: linear-gradient(135deg, #fff 0%, transparent 50%);
                }
                
                .alert-notification-header {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    margin-bottom: 8px;
                }
                
                .alert-notification-level {
                    font-size: 12px;
                    font-weight: 600;
                    padding: 2px 8px;
                    border-radius: 4px;
                    background: rgba(255, 255, 255, 0.25);
                }
                
                .alert-notification-time {
                    font-size: 11px;
                    opacity: 0.8;
                    margin-left: auto;
                }
                
                .alert-notification-title {
                    font-size: 14px;
                    font-weight: 600;
                    margin-bottom: 6px;
                    line-height: 1.4;
                }
                
                .alert-notification-content {
                    font-size: 13px;
                    opacity: 0.9;
                    margin-bottom: 12px;
                    line-height: 1.5;
                }
                
                .alert-notification-meta {
                    display: flex;
                    gap: 12px;
                    font-size: 12px;
                    opacity: 0.85;
                    margin-bottom: 12px;
                }
                
                .alert-notification-actions {
                    display: flex;
                    gap: 8px;
                }
                
                .alert-notification-btn {
                    flex: 1;
                    padding: 8px 16px;
                    border-radius: 6px;
                    font-size: 13px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: all 0.2s ease;
                    border: none;
                    text-align: center;
                }
                
                .alert-notification-btn-primary {
                    background: rgba(255, 255, 255, 0.95);
                    color: #333;
                }
                
                .alert-notification-btn-primary:hover {
                    background: #fff;
                    transform: translateY(-1px);
                }
                
                .alert-notification-btn-secondary {
                    background: rgba(255, 255, 255, 0.2);
                    color: #fff;
                    border: 1px solid rgba(255, 255, 255, 0.3);
                }
                
                .alert-notification-btn-secondary:hover {
                    background: rgba(255, 255, 255, 0.3);
                }
                
                .alert-notification-icon {
                    width: 20px;
                    height: 20px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                
                #alert-notification-container::-webkit-scrollbar {
                    width: 4px;
                }
                
                #alert-notification-container::-webkit-scrollbar-track {
                    background: transparent;
                }
                
                #alert-notification-container::-webkit-scrollbar-thumb {
                    background: rgba(255, 255, 255, 0.3);
                    border-radius: 2px;
                }
            `;
            document.head.appendChild(style);
        },

        _loadPendingAlerts() {
            try {
                const stored = localStorage.getItem(this._storageKey);
                if (stored) {
                    const alerts = JSON.parse(stored);
                    alerts.forEach(alert => this._showAlert(alert, false));
                    this._alerts = alerts;
                }
            } catch (e) {
                console.error('加载待处理告警失败:', e);
            }
        },

        _savePendingAlerts() {
            try {
                localStorage.setItem(this._storageKey, JSON.stringify(this._alerts));
            } catch (e) {
                console.error('保存待处理告警失败:', e);
            }
        },

        _connectWebSocket() {
            try {
                if (typeof SockJS === 'undefined') {
                    console.error('[AlertNotification] SockJS 未加载，无法建立 WebSocket 连接');
                    this._scheduleReconnect();
                    return;
                }
                if (typeof Stomp === 'undefined') {
                    console.error('[AlertNotification] Stomp 未加载，无法建立 WebSocket 连接');
                    this._scheduleReconnect();
                    return;
                }
                
                console.log('[AlertNotification] 正在建立 WebSocket 连接...');
                this._socket = new SockJS('/ws/alert');
                const stompClient = Stomp.over(this._socket);
                
                stompClient.debug = null;
                
                stompClient.connect({}, 
                    (frame) => {
                        console.log('[AlertNotification] WebSocket 连接成功');
                        this._reconnectAttempts = 0;
                        
                        stompClient.subscribe('/topic/alerts', (message) => {
                            try {
                                const alert = JSON.parse(message.body);
                                console.log('[AlertNotification] 收到告警消息:', alert);
                                this.addAlert(alert);
                            } catch (e) {
                                console.error('[AlertNotification] 解析告警消息失败:', e);
                            }
                        });
                    },
                    (error) => {
                        console.error('[AlertNotification] WebSocket 连接失败:', error);
                        this._scheduleReconnect();
                    }
                );
                
                this._stompClient = stompClient;
            } catch (e) {
                console.error('[AlertNotification] WebSocket 初始化失败:', e);
                this._scheduleReconnect();
            }
        },

        _scheduleReconnect() {
            if (this._reconnectAttempts < this._maxReconnectAttempts) {
                this._reconnectAttempts++;
                console.log(`WebSocket重连中... (${this._reconnectAttempts}/${this._maxReconnectAttempts})`);
                setTimeout(() => this._connectWebSocket(), this._reconnectDelay);
            }
        },

        addAlert(alert) {
            const exists = this._alerts.some(a => a.alertId === alert.alertId);
            if (exists) return;
            
            this._alerts.push(alert);
            this._savePendingAlerts();
            this._showAlert(alert, true);
        },

        _showAlert(alert, animate = true) {
            const card = document.createElement('div');
            const levelClass = this._getLevelClass(alert.alertLevel);
            const levelText = this._getLevelText(alert.alertLevel);
            card.className = `alert-notification-card ${levelClass}`;
            card.dataset.alertId = alert.alertId;
            
            const time = alert.createTime ? this._formatTime(alert.createTime) : '刚刚';
            
            card.innerHTML = `
                <div class="alert-notification-header">
                    <div class="alert-notification-icon">
                        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                            <path d="M12 2L1 21h22L12 2zm0 3.99L19.53 19H4.47L12 5.99zM11 10v4h2v-4h-2zm0 6v2h2v-2h-2z"/>
                        </svg>
                    </div>
                    <span class="alert-notification-level">${levelText}</span>
                    <span class="alert-notification-time">${time}</span>
                </div>
                <div class="alert-notification-title">${this._escapeHtml(alert.alertTitle || '安全告警')}</div>
                <div class="alert-notification-content">${this._escapeHtml(alert.alertContent || '')}</div>
                <div class="alert-notification-meta">
                    <span>来源: ${this._escapeHtml(alert.sourceIp || '-')}</span>
                    <span>类型: ${this._getAttackTypeText(alert.attackType)}</span>
                </div>
                <div class="alert-notification-actions">
                    <button class="alert-notification-btn alert-notification-btn-secondary" data-action="dismiss">知道了</button>
                    <button class="alert-notification-btn alert-notification-btn-primary" data-action="handle">去处理</button>
                </div>
            `;
            
            card.querySelectorAll('button').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const action = e.target.dataset.action;
                    this._handleAction(alert, action, card);
                });
            });
            
            this._container.appendChild(card);
        },
        
        _getLevelClass(level) {
            switch (level) {
                case 'CRITICAL': return 'alert-critical';
                case 'HIGH': return 'alert-high';
                case 'MEDIUM': return 'alert-medium';
                case 'LOW': return 'alert-low';
                default: return 'alert-medium';
            }
        },
        
        _getLevelText(level) {
            switch (level) {
                case 'CRITICAL': return '严重';
                case 'HIGH': return '高风险';
                case 'MEDIUM': return '中风险';
                case 'LOW': return '低风险';
                default: return '中风险';
            }
        },
        
        _getAttackTypeText(type) {
            if (!type) return '-';
            const typeMap = {
                'SQL_INJECTION': 'SQL注入',
                'XSS': '跨站脚本',
                'COMMAND_INJECTION': '命令注入',
                'PATH_TRAVERSAL': '路径遍历',
                'FILE_INCLUSION': '文件包含',
                'DDOS': 'DDoS攻击',
                'BRUTE_FORCE': '暴力破解',
                'SCANNER': '扫描器探测'
            };
            return typeMap[type] || type;
        },

        _handleAction(alert, action, card) {
            card.classList.add('removing');
            
            setTimeout(() => {
                card.remove();
                this._alerts = this._alerts.filter(a => a.alertId !== alert.alertId);
                this._savePendingAlerts();
            }, 300);
            
            if (action === 'handle') {
                window.location.href = '/alert-manage?alertId=' + alert.alertId;
            }
        },

        _formatTime(timeStr) {
            try {
                const date = new Date(timeStr);
                const now = new Date();
                const diff = now - date;
                
                if (diff < 60000) return '刚刚';
                if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
                if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
                
                return date.toLocaleString('zh-CN', { 
                    month: '2-digit', 
                    day: '2-digit', 
                    hour: '2-digit', 
                    minute: '2-digit' 
                });
            } catch (e) {
                return timeStr;
            }
        },

        _escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        },

        dismissAll() {
            this._container.querySelectorAll('.alert-notification-card').forEach(card => {
                card.classList.add('removing');
                setTimeout(() => card.remove(), 300);
            });
            this._alerts = [];
            this._savePendingAlerts();
        }
    };

    window.AlertNotification = AlertNotification;
})();
