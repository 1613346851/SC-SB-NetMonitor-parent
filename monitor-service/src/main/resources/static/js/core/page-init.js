/**
 * 网络监测系统 - 页面初始化模块
 * 提供统一的页面初始化、侧边栏高亮、用户信息显示等功能
 */

(function() {
    const SIDEBAR_STATE_KEY = 'sidebar_collapsed';
    const SIDEBAR_SCROLL_KEY = 'sidebar_scroll_position';
    const USER_MENUS_KEY = 'user_menus';
    
    const ALL_MENUS = [
        { id: 1, name: '仪表盘', path: '/', icon: '📊', permission: 'dashboard' },
        { id: 2, name: '流量列表', path: '/traffic', icon: '🌐', permission: 'traffic' },
        { id: 3, name: '态势感知', path: '/event', icon: '🎯', permission: 'event' },
        { id: 4, name: '攻击监测', path: '/attack', icon: '⚠️', permission: 'attack' },
        { id: 5, name: '漏洞监测', path: '/vulnerability', icon: '🔓', permission: 'vulnerability' },
        { id: 6, name: '漏洞扫描', path: '/scan', icon: 'SC', permission: 'scan' },
        { id: 7, name: '防御日志', path: '/defense', icon: '🛡️', permission: 'defense' },
        { id: 8, name: '防御效果评估', path: '/defense-evaluation', icon: '📈', permission: 'defense-evaluation' },
        { id: 9, name: '规则管理', path: '/rule', icon: '📋', permission: 'rule' },
        { id: 10, name: '黑名单管理', path: '/blacklist', icon: '🚫', permission: 'blacklist' },
        { id: 11, name: '告警管理', path: '/alert', icon: '🔔', permission: 'alert' },
        { id: 12, name: '溯源查询', path: '/trace', icon: '🔍', permission: 'trace' },
        { id: 13, name: '数据报表', path: '/report', icon: '📉', permission: 'report' },
        { id: 14, name: '系统配置', path: '/config', icon: '⚙️', permission: 'config', dividerBefore: true, groupTitle: '系统管理' },
        { id: 15, name: '用户管理', path: '/system/user', icon: '👥', permission: 'user' },
        { id: 16, name: '角色管理', path: '/system/role', icon: '🔑', permission: 'role' },
        { id: 17, name: '操作日志', path: '/system/log', icon: '📝', permission: 'operlog' }
    ];
    
    const PageInit = {
        _userMenus: null,
        
        init(options = {}) {
            const {
                requireAuth = true,
                onPageLoad = () => {},
                highlightMenu = true,
                enableAlertNotification = true
            } = options;
            
            if (requireAuth && !AuthGuard.init()) {
                return;
            }
            
            this.restoreSidebarState();
            
            this.loadAndRenderMenus().then(() => {
                if (highlightMenu) {
                    this.highlightCurrentMenu();
                }
                this.initMobileSidebar();
            });
            
            this.initUserInfo();
            this.initLogout();
            this.initLogoRipple();
            this.initSidebarToggle();
            this.saveScrollPositionOnLeave();
            
            if (enableAlertNotification) {
                this.initAlertNotification();
            }
            
            document.addEventListener('DOMContentLoaded', () => {
                onPageLoad();
            });
        },
        
        async loadAndRenderMenus() {
            try {
                const menus = await this.loadUserMenus();
                this._userMenus = menus;
                this.renderSidebarMenu(menus);
            } catch (error) {
                console.error('[PageInit] 加载菜单权限失败:', error);
                this.renderSidebarMenu([]);
            }
        },
        
        async loadUserMenus() {
            try {
                const currentUser = StorageUtil.get(AppConfig.AUTH.USER_KEY);
                const cachedUserId = StorageUtil.get(USER_MENUS_KEY + '_uid');
                const currentUserId = currentUser ? String(currentUser.id) : null;
                
                if (currentUserId && cachedUserId === currentUserId) {
                    const cachedMenus = StorageUtil.get(USER_MENUS_KEY);
                    if (cachedMenus && Array.isArray(cachedMenus) && cachedMenus.length > 0) {
                        return cachedMenus;
                    }
                }
                
                const menus = await HttpClient.get('/auth/menus');
                if (menus && Array.isArray(menus)) {
                    StorageUtil.set(USER_MENUS_KEY, menus);
                    if (currentUserId) {
                        StorageUtil.set(USER_MENUS_KEY + '_uid', currentUserId);
                    }
                    return menus;
                }
                return [];
            } catch (error) {
                console.error('[PageInit] 获取用户菜单失败:', error);
                return [];
            }
        },
        
        renderSidebarMenu(userMenus) {
            const container = document.getElementById('sidebarMenu');
            if (!container) return;
            
            const userPaths = new Set(userMenus.map(m => m.path));
            const isSuperAdmin = userMenus.length >= ALL_MENUS.length;
            
            let html = '';
            
            ALL_MENUS.forEach(menu => {
                if (menu.dividerBefore) {
                    html += '<div class="menu-divider"></div>';
                }
                if (menu.groupTitle) {
                    html += `<div class="menu-group-title">${menu.groupTitle}</div>`;
                }
                
                const hasPermission = isSuperAdmin || userPaths.has(menu.path);
                if (!hasPermission) return;
                
                html += `
                    <a href="${menu.path}" class="menu-item" data-title="${menu.name}" data-permission="${menu.permission}">
                        <span class="menu-icon">${menu.icon}</span>
                        <span>${menu.name}</span>
                    </a>
                `;
            });
            
            container.innerHTML = html;
        },
        
        hasMenuPermission(permission) {
            if (!this._userMenus) return true;
            return this._userMenus.some(m => m.permission === permission);
        },
        
        getUserMenus() {
            return this._userMenus || [];
        },
        
        clearMenuCache() {
            StorageUtil.remove(USER_MENUS_KEY);
            StorageUtil.remove(USER_MENUS_KEY + '_uid');
            this._userMenus = null;
        },
        
        async initAlertNotification() {
            try {
                if (typeof ResourceLoader === 'undefined') {
                    await this._loadResourceLoader();
                }
                
                if (typeof ResourceLoader === 'undefined') {
                    console.warn('[PageInit] ResourceLoader 加载失败，跳过告警通知初始化');
                    return;
                }
                
                await ResourceLoader.loadWebSocket();
                const script = document.createElement('script');
                script.src = '/js/components/alert-notification.js';
                script.onload = () => {
                    console.log('[PageInit] 告警通知组件加载成功');
                    if (window.AlertNotification) {
                        window.AlertNotification.init();
                    }
                };
                script.onerror = () => {
                    console.error('[PageInit] 告警通知组件加载失败');
                };
                document.head.appendChild(script);
            } catch (e) {
                console.error('[PageInit] 初始化告警通知失败:', e);
            }
        },
        
        _loadResourceLoader() {
            return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = '/js/core/resource-loader.js';
                script.onload = () => {
                    console.log('[PageInit] ResourceLoader 加载成功');
                    resolve();
                };
                script.onerror = () => {
                    console.error('[PageInit] ResourceLoader 加载失败');
                    reject(new Error('Failed to load ResourceLoader'));
                };
                document.head.appendChild(script);
            });
        },
        
        restoreSidebarState() {
            const sidebar = document.querySelector('.sidebar');
            if (!sidebar) return;
            
            const isCollapsed = localStorage.getItem(SIDEBAR_STATE_KEY) === 'true';
            if (isCollapsed) {
                sidebar.classList.add('collapsed');
            }
            
            const sidebarMenu = document.querySelector('.sidebar-menu');
            if (sidebarMenu) {
                const savedScroll = localStorage.getItem(SIDEBAR_SCROLL_KEY);
                if (savedScroll) {
                    sidebarMenu.scrollTop = parseInt(savedScroll, 10);
                }
            }
        },
        
        saveScrollPositionOnLeave() {
            window.addEventListener('beforeunload', () => {
                const sidebarMenu = document.querySelector('.sidebar-menu');
                if (sidebarMenu) {
                    localStorage.setItem(SIDEBAR_SCROLL_KEY, sidebarMenu.scrollTop);
                }
            });
        },
        
        initSidebarToggle() {
            const toggleBtn = document.getElementById('sidebarToggle');
            const sidebar = document.querySelector('.sidebar');
            
            if (!toggleBtn || !sidebar) return;
            
            toggleBtn.addEventListener('click', () => {
                sidebar.classList.toggle('collapsed');
                const isCollapsed = sidebar.classList.contains('collapsed');
                localStorage.setItem(SIDEBAR_STATE_KEY, isCollapsed);
            });
        },
        
        initMobileSidebar() {
            const mobileToggle = document.getElementById('mobileMenuToggle');
            const closeBtn = document.getElementById('sidebarClose');
            const sidebar = document.querySelector('.sidebar');
            const overlay = document.querySelector('.sidebar-overlay');
            
            if (!sidebar) return;
            
            const closeSidebar = () => {
                sidebar.classList.remove('open');
                if (overlay) {
                    overlay.classList.remove('show');
                }
            };
            
            if (mobileToggle) {
                mobileToggle.addEventListener('click', () => {
                    sidebar.classList.toggle('open');
                    if (overlay) {
                        overlay.classList.toggle('show');
                    }
                });
            }
            
            if (closeBtn) {
                closeBtn.addEventListener('click', closeSidebar);
            }
            
            if (overlay) {
                overlay.addEventListener('click', closeSidebar);
            }
            
            const menuItems = sidebar.querySelectorAll('.menu-item');
            menuItems.forEach(item => {
                item.addEventListener('click', () => {
                    if (window.innerWidth <= 768) {
                        closeSidebar();
                    }
                });
            });
        },
        
        initLogoRipple() {
            const logo = document.getElementById('sidebarLogo');
            if (logo) {
                logo.addEventListener('click', (e) => {
                    const rect = logo.getBoundingClientRect();
                    const size = Math.max(rect.width, rect.height);
                    const x = e.clientX - rect.left - size / 2;
                    const y = e.clientY - rect.top - size / 2;
                    
                    const ripple = document.createElement('span');
                    ripple.className = 'ripple';
                    ripple.style.width = ripple.style.height = size + 'px';
                    ripple.style.left = x + 'px';
                    ripple.style.top = y + 'px';
                    
                    logo.appendChild(ripple);
                    
                    ripple.addEventListener('animationend', () => {
                        ripple.remove();
                    });
                });
            }
        },
        
        highlightCurrentMenu() {
            const currentPath = window.location.pathname;
            const menuItems = document.querySelectorAll('.menu-item');
            const sidebarMenu = document.querySelector('.sidebar-menu');
            
            menuItems.forEach(item => {
                const href = item.getAttribute('href');
                item.classList.remove('active');
            });
            
            let bestMatch = null;
            let bestMatchLength = 0;
            
            menuItems.forEach(item => {
                const href = item.getAttribute('href');
                if (href === currentPath) {
                    bestMatch = item;
                    bestMatchLength = href.length;
                } else if (currentPath.startsWith(href + '/') || 
                           (currentPath.startsWith(href) && href !== '/' && currentPath.length === href.length)) {
                    if (href.length > bestMatchLength) {
                        bestMatch = item;
                        bestMatchLength = href.length;
                    }
                }
            });
            
            if (bestMatch) {
                bestMatch.classList.add('active');
                this.scrollToMenuItem(bestMatch, sidebarMenu);
            }
        },
        
        scrollToMenuItem(menuItem, container) {
            if (!menuItem || !container) return;
            
            const itemTop = menuItem.offsetTop;
            const itemHeight = menuItem.offsetHeight;
            const containerHeight = container.clientHeight;
            const scrollTop = container.scrollTop;
            
            const itemBottom = itemTop + itemHeight;
            const visibleBottom = scrollTop + containerHeight;
            
            if (itemTop < scrollTop || itemBottom > visibleBottom) {
                const targetScroll = itemTop - (containerHeight / 2) + (itemHeight / 2);
                container.scrollTop = Math.max(0, targetScroll);
            }
        },
        
        initUserInfo() {
            const user = AuthService.getUser();
            if (user) {
                const avatarEl = document.querySelector('.user-avatar');
                const nameEl = document.querySelector('.user-name');
                
                const displayName = user.nickname || user.username || '用户';
                
                if (avatarEl) {
                    avatarEl.textContent = displayName.charAt(0).toUpperCase();
                }
                if (nameEl) {
                    nameEl.textContent = displayName;
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
                            <div class="spinner" style="width: 40px; height: 40px; border: 4px solid #e8e8e8; border-top-color: #4f46e5; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;"></div>
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
})();
