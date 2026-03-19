/**
 * 网络监测系统 - 页面初始化模块
 * 提供统一的页面初始化、侧边栏高亮、用户信息显示等功能
 */

const PageInit = {
    init(options = {}) {
        const {
            requireAuth = true,
            onPageLoad = () => {},
            highlightMenu = true
        } = options;
        
        if (requireAuth && !AuthGuard.init()) {
            return;
        }
        
        if (highlightMenu) {
            this.highlightCurrentMenu();
        }
        
        this.initUserInfo();
        this.initLogout();
        
        document.addEventListener('DOMContentLoaded', () => {
            onPageLoad();
        });
    },
    
    highlightCurrentMenu() {
        const currentPath = window.location.pathname;
        const menuItems = document.querySelectorAll('.menu-item');
        
        menuItems.forEach(item => {
            const href = item.getAttribute('href');
            if (href === currentPath || (currentPath.startsWith(href) && href !== '/')) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
    },
    
    initUserInfo() {
        const user = AuthService.getUser();
        if (user) {
            const avatarEl = document.querySelector('.user-avatar');
            const nameEl = document.querySelector('.user-name');
            
            if (avatarEl) {
                avatarEl.textContent = (user.username || user.realName || 'A').charAt(0).toUpperCase();
            }
            if (nameEl) {
                nameEl.textContent = user.realName || user.username || '管理员';
            }
        }
    },
    
    initLogout() {
        const logoutBtn = document.querySelector('.logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', async (e) => {
                e.preventDefault();
                
                ModalUtil.confirm({
                    title: '退出确认',
                    message: '确定要退出登录吗？',
                    confirmText: '确定',
                    cancelText: '取消',
                    type: 'default',
                    onConfirm: async () => {
                        await AuthService.logout();
                    }
                });
            });
        }
    },
    
    showLoading() {
        let loadingEl = document.getElementById('pageLoading');
        if (!loadingEl) {
            loadingEl = document.createElement('div');
            loadingEl.id = 'pageLoading';
            loadingEl.innerHTML = `
                <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(255,255,255,0.8); z-index: 9999; display: flex; align-items: center; justify-content: center;">
                    <div style="text-align: center;">
                        <div class="spinner" style="width: 40px; height: 40px; border: 4px solid #e8e8e8; border-top-color: #1890ff; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;"></div>
                        <div style="margin-top: 12px; color: #666;">加载中...</div>
                    </div>
                </div>
            `;
            document.body.appendChild(loadingEl);
        }
        loadingEl.style.display = 'block';
    },
    
    hideLoading() {
        const loadingEl = document.getElementById('pageLoading');
        if (loadingEl) {
            loadingEl.style.display = 'none';
        }
    }
};

const TimeRangeHelper = {
    getAutoInterval(timeRange) {
        const recommendations = {
            '1h': '5m',
            '6h': '10m',
            '12h': '30m',
            '24h': '30m',
            '3d': '1h',
            '7d': '1h',
            '14d': '1d',
            '30d': '1d'
        };
        return recommendations[timeRange] || '30m';
    },
    
    getIntervalDisplay(timeRange) {
        const displays = {
            '1h': '5 分钟',
            '6h': '10 分钟',
            '12h': '30 分钟',
            '24h': '30 分钟',
            '3d': '1 小时',
            '7d': '1 小时',
            '14d': '1 天',
            '30d': '1 天'
        };
        return displays[timeRange] || '30 分钟';
    }
};

window.PageInit = PageInit;
window.TimeRangeHelper = TimeRangeHelper;
