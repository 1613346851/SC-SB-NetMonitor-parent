/**
 * 网络监测系统 - 模态框组件
 * 提供统一的模态框管理
 */

const ModalUtil = {
    _stack: [],
    
    create(options = {}) {
        const {
            title = '',
            content = '',
            width = '600px',
            closable = true,
            footer = null,
            onClose = null,
            className = ''
        } = options;
        
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.45);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: ${1000 + this._stack.length};
            animation: fadeIn 0.2s ease;
        `;
        
        const modal = document.createElement('div');
        modal.className = `modal ${className}`;
        modal.style.cssText = `
            background: #fff;
            border-radius: 4px;
            width: ${width};
            max-width: 90%;
            max-height: 90vh;
            overflow-y: auto;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            animation: slideUp 0.3s ease;
        `;
        
        let html = `
            <div class="modal-header" style="padding: 16px 24px; border-bottom: 1px solid #e8e8e8; display: flex; justify-content: space-between; align-items: center;">
                <span class="modal-title" style="font-size: 16px; font-weight: 600;">${title}</span>
                ${closable ? '<span class="modal-close" style="cursor: pointer; font-size: 20px; color: #999;">&times;</span>' : ''}
            </div>
            <div class="modal-body" style="padding: 24px;">${content}</div>
        `;
        
        if (footer) {
            html += `
                <div class="modal-footer" style="padding: 16px 24px; border-top: 1px solid #e8e8e8; display: flex; justify-content: flex-end; gap: 8px;">
                    ${footer}
                </div>
            `;
        }
        
        modal.innerHTML = html;
        overlay.appendChild(modal);
        document.body.appendChild(overlay);
        
        const modalInstance = {
            overlay,
            modal,
            close: () => this.close(overlay, onClose)
        };
        
        this._stack.push(modalInstance);
        
        if (closable) {
            const closeBtn = modal.querySelector('.modal-close');
            closeBtn.addEventListener('click', () => this.close(overlay, onClose));
            
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    this.close(overlay, onClose);
                }
            });
        }
        
        document.body.style.overflow = 'hidden';
        
        return modalInstance;
    },
    
    close(overlay, callback) {
        if (!overlay) return;
        
        overlay.style.animation = 'fadeOut 0.2s ease forwards';
        
        setTimeout(() => {
            overlay.remove();
            this._stack = this._stack.filter(m => m.overlay !== overlay);
            
            if (this._stack.length === 0) {
                document.body.style.overflow = '';
            }
            
            if (callback) callback();
        }, 200);
    },
    
    closeAll() {
        this._stack.forEach(m => this.close(m.overlay));
    },
    
    confirm(options = {}) {
        const {
            title = '确认',
            message = '确定要执行此操作吗？',
            confirmText = '确定',
            cancelText = '取消',
            type = 'default',
            onConfirm = () => {},
            onCancel = () => {}
        } = options;
        
        const typeColors = {
            default: 'btn-primary',
            danger: 'btn-danger',
            warning: 'btn-warning'
        };
        
        const modal = this.create({
            title,
            content: `<p style="margin: 0;">${message}</p>`,
            width: '400px',
            footer: `
                <button class="btn btn-default" data-action="cancel">${cancelText}</button>
                <button class="btn ${typeColors[type] || 'btn-primary'}" data-action="confirm">${confirmText}</button>
            `
        });
        
        modal.modal.querySelector('[data-action="cancel"]').addEventListener('click', () => {
            modal.close();
            onCancel();
        });
        
        modal.modal.querySelector('[data-action="confirm"]').addEventListener('click', () => {
            modal.close();
            onConfirm();
        });
        
        return modal;
    },
    
    alert(options = {}) {
        const {
            title = '提示',
            message = '',
            buttonText = '确定',
            type = 'info',
            onClose = () => {}
        } = options;
        
        const typeColors = {
            info: 'btn-primary',
            success: 'btn-success',
            warning: 'btn-warning',
            error: 'btn-danger'
        };
        
        const modal = this.create({
            title,
            content: `<p style="margin: 0;">${message}</p>`,
            width: '400px',
            footer: `<button class="btn ${typeColors[type] || 'btn-primary'}" data-action="close">${buttonText}</button>`
        });
        
        modal.modal.querySelector('[data-action="close"]').addEventListener('click', () => {
            modal.close();
            onClose();
        });
        
        return modal;
    }
};

const modalStyles = document.createElement('style');
modalStyles.textContent = `
    @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
    }
    @keyframes fadeOut {
        from { opacity: 1; }
        to { opacity: 0; }
    }
    @keyframes slideUp {
        from { opacity: 0; transform: translateY(20px); }
        to { opacity: 1; transform: translateY(0); }
    }
`;
document.head.appendChild(modalStyles);

window.ModalUtil = ModalUtil;
window.modal = ModalUtil;
