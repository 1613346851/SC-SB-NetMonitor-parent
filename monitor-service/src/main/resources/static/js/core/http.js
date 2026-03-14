/**
 * 网络监测系统 - HTTP 客户端
 * 基于 Fetch API 封装，支持拦截器、认证、错误处理
 */

const HttpClient = {
    baseUrl: AppConfig.API_BASE_URL,
    interceptors: {
        request: [],
        response: []
    },
    
    useRequestInterceptor(fn) {
        this.interceptors.request.push(fn);
    },
    
    useResponseInterceptor(fn) {
        this.interceptors.response.push(fn);
    },
    
    async _request(url, options = {}) {
        let fullUrl = url.startsWith('http') ? url : `${this.baseUrl}${url}`;
        
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json'
            }
        };
        
        let finalOptions = { ...defaultOptions, ...options };
        
        for (const interceptor of this.interceptors.request) {
            const result = await interceptor(fullUrl, finalOptions);
            if (result) {
                fullUrl = result.url || fullUrl;
                finalOptions = result.options || finalOptions;
            }
        }
        
        try {
            const response = await fetch(fullUrl, finalOptions);
            let data;
            
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }
            
            for (const interceptor of this.interceptors.response) {
                const result = await interceptor(response, data);
                if (result !== undefined) {
                    data = result;
                }
            }
            
            if (!response.ok) {
                const error = new Error(data.message || data || '请求失败');
                error.status = response.status;
                error.data = data;
                throw error;
            }
            
            if (data && typeof data === 'object' && 'code' in data) {
                if (data.code === 200 || data.code === 0) {
                    return data.data;
                } else {
                    throw new Error(data.message || '请求失败');
                }
            }
            
            return data;
        } catch (error) {
            if (error.status === 401) {
                AuthService.handleUnauthorized();
            }
            throw error;
        }
    },
    
    async get(url, params = {}) {
        const queryString = new URLSearchParams(
            Object.entries(params).filter(([_, v]) => v !== null && v !== undefined && v !== '')
        ).toString();
        
        const fullUrl = queryString ? `${url}?${queryString}` : url;
        
        return this._request(fullUrl, { method: 'GET' });
    },
    
    async post(url, data = {}) {
        return this._request(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },
    
    async put(url, data = {}) {
        return this._request(url, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },
    
    async delete(url, params = {}) {
        const queryString = new URLSearchParams(
            Object.entries(params).filter(([_, v]) => v !== null && v !== undefined && v !== '')
        ).toString();
        
        const fullUrl = queryString ? `${url}?${queryString}` : url;
        
        return this._request(fullUrl, { method: 'DELETE' });
    },
    
    async upload(url, formData) {
        const options = {
            method: 'POST',
            body: formData
        };
        
        delete options.headers;
        
        return this._request(url, options);
    }
};

HttpClient.useRequestInterceptor((url, options) => {
    const token = AuthService.getToken();
    if (token) {
        options.headers = options.headers || {};
        options.headers[AppConfig.AUTH.TOKEN_HEADER] = `${AppConfig.AUTH.TOKEN_PREFIX}${token}`;
    }
    return { url, options };
});

HttpClient.useResponseInterceptor((response, data) => {
    if (response.status === 401) {
        AuthService.handleUnauthorized();
        return null;
    }
    return data;
});

window.HttpClient = HttpClient;
window.http = HttpClient;
