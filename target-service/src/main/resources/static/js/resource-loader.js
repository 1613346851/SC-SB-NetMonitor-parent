(function() {
    const ResourceLoader = {
        loadedResources: new Set(),
        failedResources: new Set(),
        
        cdnFallbacks: {
            bootstrapCss: [
                '/lib/bootstrap/bootstrap.min.css',
                'https://cdn.staticfile.org/bootstrap/5.1.3/css/bootstrap.min.css',
                'https://lib.baomitu.com/bootstrap/5.1.3/css/bootstrap.min.css'
            ],
            bootstrapJs: [
                '/lib/bootstrap/bootstrap.bundle.min.js',
                'https://cdn.staticfile.org/bootstrap/5.1.3/js/bootstrap.bundle.min.js',
                'https://lib.baomitu.com/bootstrap/5.1.3/js/bootstrap.bundle.min.js'
            ],
            fontawesome: [
                '/lib/fontawesome/all.min.css',
                'https://cdn.staticfile.org/font-awesome/6.4.0/css/all.min.css',
                'https://lib.baomitu.com/font-awesome/6.4.0/css/all.min.css'
            ]
        },
        
        async loadCss(src) {
            return new Promise((resolve, reject) => {
                if (this.loadedResources.has(src)) {
                    resolve(true);
                    return;
                }
                
                const link = document.createElement('link');
                link.rel = 'stylesheet';
                link.href = src;
                
                link.onload = () => {
                    this.loadedResources.add(src);
                    console.log('[ResourceLoader] CSS加载成功:', src);
                    resolve(true);
                };
                
                link.onerror = () => {
                    this.failedResources.add(src);
                    console.warn('[ResourceLoader] CSS加载失败:', src);
                    reject(new Error('Failed to load CSS: ' + src));
                };
                
                document.head.appendChild(link);
            });
        },
        
        async loadScript(src) {
            return new Promise((resolve, reject) => {
                if (this.loadedResources.has(src)) {
                    resolve(true);
                    return;
                }
                
                const script = document.createElement('script');
                script.src = src;
                
                script.onload = () => {
                    this.loadedResources.add(src);
                    console.log('[ResourceLoader] JS加载成功:', src);
                    resolve(true);
                };
                
                script.onerror = () => {
                    this.failedResources.add(src);
                    console.warn('[ResourceLoader] JS加载失败:', src);
                    reject(new Error('Failed to load JS: ' + src));
                };
                
                document.head.appendChild(script);
            });
        },
        
        async loadWithFallback(name, type) {
            const fallbacks = this.cdnFallbacks[name];
            if (!fallbacks || fallbacks.length === 0) {
                throw new Error('No fallbacks defined for: ' + name);
            }
            
            for (let i = 0; i < fallbacks.length; i++) {
                const src = fallbacks[i];
                try {
                    if (type === 'css') {
                        await this.loadCss(src);
                    } else {
                        await this.loadScript(src);
                    }
                    console.log('[ResourceLoader] ' + name + ' 加载成功，使用:', src);
                    return true;
                } catch (e) {
                    console.warn('[ResourceLoader] ' + name + ' 尝试 ' + (i + 1) + '/' + fallbacks.length + ' 失败:', src);
                    if (i === fallbacks.length - 1) {
                        console.error('[ResourceLoader] ' + name + ' 所有资源加载失败');
                        this.showLoadError(name);
                        throw e;
                    }
                }
            }
        },
        
        showLoadError(resourceName) {
            var existingError = document.getElementById('resource-load-error');
            if (existingError) return;
            
            var errorDiv = document.createElement('div');
            errorDiv.id = 'resource-load-error';
            errorDiv.style.cssText = 'position:fixed;top:0;left:0;right:0;background:#fee2e2;color:#991b1b;padding:12px 20px;text-align:center;z-index:99999;font-size:14px;border-bottom:1px solid #fca5a5;';
            errorDiv.innerHTML = '<span>资源加载失败 (' + resourceName + ')，请检查网络连接或刷新页面重试</span><button onclick="location.reload()" style="margin-left:12px;padding:4px 12px;cursor:pointer;border:1px solid #991b1b;background:transparent;border-radius:4px;">刷新</button>';
            document.body.insertBefore(errorDiv, document.body.firstChild);
        },
        
        async loadAll() {
            try {
                await this.loadWithFallback('bootstrapCss', 'css');
                await this.loadWithFallback('fontawesome', 'css');
                await this.loadWithFallback('bootstrapJs', 'js');
                return true;
            } catch (e) {
                console.error('[ResourceLoader] 部分资源加载失败:', e);
                return false;
            }
        }
    };
    
    window.ResourceLoader = ResourceLoader;
})();
