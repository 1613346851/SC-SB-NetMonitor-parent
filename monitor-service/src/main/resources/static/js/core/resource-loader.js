(function() {
    const ResourceLoader = {
        loadedResources: new Set(),
        failedResources: new Set(),
        
        cdnFallbacks: {
            echarts: [
                '/js/lib/echarts.min.js',
                'https://cdn.bootcdn.net/ajax/libs/echarts/5.4.3/echarts.min.js',
                'https://lib.baomitu.com/echarts/5.4.3/echarts.min.js',
                'https://cdn.staticfile.org/echarts/5.4.3/echarts.min.js'
            ]
        },
        
        async loadScript(src, options = {}) {
            return new Promise((resolve, reject) => {
                if (this.loadedResources.has(src)) {
                    resolve(true);
                    return;
                }
                
                const script = document.createElement('script');
                script.src = src;
                script.async = options.async !== false;
                script.defer = options.defer || false;
                
                script.onload = () => {
                    this.loadedResources.add(src);
                    console.log(`[ResourceLoader] 加载成功: ${src}`);
                    resolve(true);
                };
                
                script.onerror = () => {
                    this.failedResources.add(src);
                    console.warn(`[ResourceLoader] 加载失败: ${src}`);
                    reject(new Error(`Failed to load: ${src}`));
                };
                
                document.head.appendChild(script);
            });
        },
        
        async loadScriptWithFallback(name, options = {}) {
            const fallbacks = this.cdnFallbacks[name];
            if (!fallbacks || fallbacks.length === 0) {
                throw new Error(`No fallbacks defined for: ${name}`);
            }
            
            for (let i = 0; i < fallbacks.length; i++) {
                const src = fallbacks[i];
                try {
                    await this.loadScript(src, options);
                    console.log(`[ResourceLoader] ${name} 加载成功，使用: ${src}`);
                    return true;
                } catch (e) {
                    console.warn(`[ResourceLoader] ${name} 尝试 ${i + 1}/${fallbacks.length} 失败: ${src}`);
                    if (i === fallbacks.length - 1) {
                        console.error(`[ResourceLoader] ${name} 所有资源加载失败`);
                        this.showLoadError(name);
                        throw e;
                    }
                }
            }
        },
        
        showLoadError(resourceName) {
            const existingError = document.getElementById('resource-load-error');
            if (existingError) return;
            
            const errorDiv = document.createElement('div');
            errorDiv.id = 'resource-load-error';
            errorDiv.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                background: #fee2e2;
                color: #991b1b;
                padding: 12px 20px;
                text-align: center;
                z-index: 99999;
                font-size: 14px;
                border-bottom: 1px solid #fca5a5;
            `;
            errorDiv.innerHTML = `
                <span>⚠️ 资源加载失败 (${resourceName})，请检查网络连接或刷新页面重试</span>
                <button onclick="location.reload()" style="margin-left: 12px; padding: 4px 12px; cursor: pointer; border: 1px solid #991b1b; background: transparent; border-radius: 4px;">刷新</button>
            `;
            document.body.insertBefore(errorDiv, document.body.firstChild);
        },
        
        async loadEcharts() {
            if (window.echarts) {
                return true;
            }
            return this.loadScriptWithFallback('echarts');
        },
        
        isLoaded(name) {
            if (name === 'echarts') {
                return typeof window.echarts !== 'undefined';
            }
            return false;
        }
    };
    
    window.ResourceLoader = ResourceLoader;
})();
