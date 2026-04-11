const VulnCommon = {
    showLoading: function(show) {
        const overlay = document.getElementById('loadingOverlay');
        if (overlay) {
            overlay.style.display = show ? 'flex' : 'none';
        }
    },

    updateStatus: function(status, text) {
        const indicator = document.getElementById('statusIndicator');
        if (indicator) {
            const statusClasses = {
                'ready': 'bg-secondary',
                'executing': 'bg-primary',
                'success': 'bg-success',
                'error': 'bg-danger'
            };
            indicator.className = `badge ${statusClasses[status] || 'bg-secondary'}`;
            indicator.innerHTML = `<i class="fas fa-circle-notch ${status === 'executing' ? 'fa-spin' : ''} me-1"></i>${text}`;
        }
    },

    showNotification: function(message, type) {
        const toast = document.createElement('div');
        toast.className = `toast align-items-center text-white bg-${type} border-0 position-fixed`;
        toast.style.cssText = 'top: 80px; right: 20px; z-index: 9999;';
        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">${this.escapeHtml(message)}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>`;
        document.body.appendChild(toast);
        
        if (typeof bootstrap !== 'undefined' && bootstrap.Toast) {
            const bsToast = new bootstrap.Toast(toast, { delay: 3000 });
            bsToast.show();
        } else {
            toast.style.cssText += '; padding: 12px 20px; border-radius: 4px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);';
            const closeBtn = toast.querySelector('.btn-close');
            if (closeBtn) {
                closeBtn.style.cssText = 'background: transparent; border: none; color: white; cursor: pointer; font-size: 16px;';
                closeBtn.innerHTML = '×';
                closeBtn.onclick = () => toast.remove();
            }
        }
        setTimeout(() => toast.remove(), 4000);
    },

    escapeHtml: function(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    formatTime: function() {
        const now = new Date();
        return now.toLocaleTimeString('zh-CN', { hour12: false }) + '.' + 
               String(now.getMilliseconds()).padStart(3, '0');
    },

    appendResult: function(containerId, htmlContent, clearWelcome = true) {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (clearWelcome) {
            const welcomeMsg = container.querySelector('.welcome-message');
            if (welcomeMsg) {
                welcomeMsg.remove();
            }
        }

        const wrapper = document.createElement('div');
        wrapper.innerHTML = htmlContent;
        container.appendChild(wrapper.firstElementChild);
        container.scrollTop = container.scrollHeight;
    },

    clearOutput: function(containerId, welcomeHtml) {
        const container = document.getElementById(containerId);
        if (container) {
            container.innerHTML = welcomeHtml || `
                <div class="welcome-message text-center text-muted d-flex flex-column justify-content-center align-items-center h-100">
                    <i class="fas fa-shield-alt fa-4x mb-4 text-primary"></i>
                    <h4 class="fw-bold text-dark">欢迎使用漏洞测试平台</h4>
                    <p class="mb-0 lead">请选择左侧测试用例开始测试</p>
                </div>`;
        }
    },

    createResultCard: function(options) {
        const { isSuccess, title, badge, content, type = 'vuln' } = options;
        const borderClass = isSuccess ? 'border-success' : 'border-danger';
        const badgeClass = type === 'vuln' ? 'bg-danger' : 'bg-success';
        const badgeText = type === 'vuln' ? '漏洞接口' : '安全接口';
        const iconClass = isSuccess ? 'check-circle text-success' : 'times-circle text-danger';
        
        return `
            <div class="result-card ${borderClass}">
                <div class="result-header d-flex justify-content-between align-items-center">
                    <h6 class="mb-0">
                        <i class="fas fa-${iconClass} me-2"></i>
                        ${this.escapeHtml(title)}
                    </h6>
                    <div>
                        <span class="badge ${badgeClass} me-2">${badgeText}</span>
                        <small class="text-muted">${this.formatTime()}</small>
                    </div>
                </div>
                <div class="result-body">
                    ${content}
                </div>
            </div>`;
    },

    createErrorCard: function(message) {
        return `
            <div class="result-card border-danger">
                <div class="result-header">
                    <h6 class="mb-0">
                        <i class="fas fa-times-circle text-danger me-2"></i>
                        请求失败
                    </h6>
                    <small class="text-muted">${this.formatTime()}</small>
                </div>
                <div class="result-body">
                    <div class="alert alert-danger mb-0">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        错误信息: ${this.escapeHtml(message)}
                    </div>
                </div>
            </div>`;
    },

    createInfoCard: function(title, content) {
        return `
            <div class="result-card border-info">
                <div class="result-header">
                    <h6 class="mb-0">
                        <i class="fas fa-info-circle text-info me-2"></i>
                        ${this.escapeHtml(title)}
                    </h6>
                    <small class="text-muted">${this.formatTime()}</small>
                </div>
                <div class="result-body">
                    ${content}
                </div>
            </div>`;
    }
};

window.VulnCommon = VulnCommon;
