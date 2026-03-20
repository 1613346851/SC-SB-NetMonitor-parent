/**
 * 网络监测系统 - 存储工具
 * 提供本地存储的统一封装，支持前缀隔离和过期时间
 */

(function() {
    const StorageUtil = {
        _prefix: AppConfig.STORAGE.PREFIX,
        
        _getKey(key) {
            return this._prefix + key;
        },
        
        set(key, value, expireMs = null) {
            const data = {
                value: value,
                timestamp: Date.now(),
                expire: expireMs
            };
            
            try {
                localStorage.setItem(this._getKey(key), JSON.stringify(data));
                return true;
            } catch (error) {
                console.error('StorageUtil.set error:', error);
                return false;
            }
        },
        
        get(key, defaultValue = null) {
            try {
                const raw = localStorage.getItem(this._getKey(key));
                if (!raw) return defaultValue;
                
                const data = JSON.parse(raw);
                
                if (data.expire && Date.now() - data.timestamp > data.expire) {
                    this.remove(key);
                    return defaultValue;
                }
                
                return data.value !== undefined ? data.value : defaultValue;
            } catch (error) {
                console.error('StorageUtil.get error:', error);
                return defaultValue;
            }
        },
        
        remove(key) {
            try {
                localStorage.removeItem(this._getKey(key));
                return true;
            } catch (error) {
                console.error('StorageUtil.remove error:', error);
                return false;
            }
        },
        
        clear() {
            try {
                const keysToRemove = [];
                for (let i = 0; i < localStorage.length; i++) {
                    const key = localStorage.key(i);
                    if (key && key.startsWith(this._prefix)) {
                        keysToRemove.push(key);
                    }
                }
                keysToRemove.forEach(key => localStorage.removeItem(key));
                return true;
            } catch (error) {
                console.error('StorageUtil.clear error:', error);
                return false;
            }
        },
        
        exists(key) {
            return localStorage.getItem(this._getKey(key)) !== null;
        }
    };

    window.StorageUtil = StorageUtil;
})();
