/**
 * 网络监测系统 - 认证服务
 * 处理用户登录、登出、Token 管理、登录状态检查
 */

(function() {
    const AuthService = {
        _tokenKey: AppConfig.AUTH.TOKEN_KEY,
        _userKey: AppConfig.AUTH.USER_KEY,
        _loginUrl: AppConfig.AUTH.LOGIN_URL,
        
        getToken() {
            return StorageUtil.get(this._tokenKey);
        },
        
        setToken(token) {
            StorageUtil.set(this._tokenKey, token);
        },
        
        removeToken() {
            StorageUtil.remove(this._tokenKey);
        },
        
        getUser() {
            return StorageUtil.get(this._userKey);
        },
        
        setUser(user) {
            StorageUtil.set(this._userKey, user);
        },
        
        removeUser() {
            StorageUtil.remove(this._userKey);
        },
        
        isAuthenticated() {
            const token = this.getToken();
            return !!token;
        },
        
        async login(username, password) {
            try {
                const response = await HttpClient.post('/auth/login', {
                    username,
                    password
                });
                
                if (response && response.token) {
                    this.setToken(response.token);
                    if (response.user) {
                        this.setUser(response.user);
                    }
                    if (window.PageInit) {
                        PageInit.clearMenuCache();
                    }
                    return { 
                        success: true, 
                        user: response.user,
                        defaultPage: response.defaultPage,
                        permittedPaths: response.permittedPaths || []
                    };
                }
                
                return { success: false, message: '登录失败' };
            } catch (error) {
                console.error('Login error:', error);
                return { success: false, message: error.message || '登录失败' };
            }
        },
        
        async logout() {
            try {
                await HttpClient.post('/auth/logout');
            } catch (error) {
                console.error('Logout error:', error);
            } finally {
                this.removeToken();
                this.removeUser();
                if (window.PageInit) {
                    PageInit.clearMenuCache();
                }
                this.redirectToLogin();
            }
        },
        
        handleUnauthorized() {
            this.removeToken();
            this.removeUser();
            this.redirectToLogin();
        },
        
        redirectToLogin() {
            const currentPath = window.location.pathname;
            const returnUrl = encodeURIComponent(currentPath);
            window.location.href = `${this._loginUrl}?returnUrl=${returnUrl}`;
        },
        
        checkAuth() {
            if (!this.isAuthenticated()) {
                this.redirectToLogin();
                return false;
            }
            return true;
        },
        
        async checkSession() {
            if (!this.isAuthenticated()) {
                return false;
            }
            
            try {
                const user = await HttpClient.get('/auth/me');
                this.setUser(user);
                return true;
            } catch (error) {
                console.error('Session check failed:', error);
                this.handleUnauthorized();
                return false;
            }
        }
    };

    const AuthGuard = {
        init() {
            if (!AuthService.isAuthenticated()) {
                AuthService.redirectToLogin();
                return false;
            }
            return true;
        },
        
        async checkPageAuth() {
            const publicPages = ['/login', '/forgot-password'];
            const currentPath = window.location.pathname;
            
            if (publicPages.some(page => currentPath.startsWith(page))) {
                return true;
            }
            
            return AuthService.checkAuth();
        }
    };

    window.AuthService = AuthService;
    window.AuthGuard = AuthGuard;
})();
