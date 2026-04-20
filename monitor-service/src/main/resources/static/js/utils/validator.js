/**
 * 网络监测系统 - 验证工具
 * 提供常用的表单验证方法
 */

(function() {
    const ValidatorUtil = {
        required(value, fieldName = '字段') {
            if (value === null || value === undefined || (typeof value === 'string' && !value.trim())) {
                MessageUtil.error(`${fieldName}不能为空`);
                return false;
            }
            return true;
        },
        
        email(value) {
            const reg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!reg.test(value)) {
                MessageUtil.error('邮箱格式不正确');
                return false;
            }
            return true;
        },
        
        phone(value) {
            const reg = /^1[3-9]\d{9}$/;
            if (!reg.test(value)) {
                MessageUtil.error('手机号格式不正确');
                return false;
            }
            return true;
        },
        
        ip(value) {
            const ipv4Reg = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/;
            const ipv6Reg = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/;
            
            if (!ipv4Reg.test(value) && !ipv6Reg.test(value)) {
                MessageUtil.error('IP 地址格式不正确');
                return false;
            }
            return true;
        },
        
        port(value) {
            const port = parseInt(value);
            if (isNaN(port) || port < 0 || port > 65535) {
                MessageUtil.error('端口号必须在 0-65535 之间');
                return false;
            }
            return true;
        },
        
        url(value) {
            try {
                new URL(value);
                return true;
            } catch {
                MessageUtil.error('URL 格式不正确');
                return false;
            }
        },
        
        minLength(value, min, fieldName = '字段') {
            if (value.length < min) {
                MessageUtil.error(`${fieldName}长度不能少于 ${min} 个字符`);
                return false;
            }
            return true;
        },
        
        maxLength(value, max, fieldName = '字段') {
            if (value.length > max) {
                MessageUtil.error(`${fieldName}长度不能超过 ${max} 个字符`);
                return false;
            }
            return true;
        },
        
        range(value, min, max, fieldName = '字段') {
            const num = parseFloat(value);
            if (isNaN(num) || num < min || num > max) {
                MessageUtil.error(`${fieldName}必须在 ${min} 到 ${max} 之间`);
                return false;
            }
            return true;
        },
        
        pattern(value, regex, message = '格式不正确') {
            if (!regex.test(value)) {
                MessageUtil.error(message);
                return false;
            }
            return true;
        },
        
        username(value) {
            const reg = /^[a-zA-Z][a-zA-Z0-9_]{2,19}$/;
            if (!reg.test(value)) {
                MessageUtil.error('用户名必须以字母开头，3-20位字母、数字或下划线');
                return false;
            }
            return true;
        },
        
        password(value) {
            if (value.length < 6) {
                MessageUtil.error('密码长度不能少于6位');
                return false;
            }
            if (value.length > 32) {
                MessageUtil.error('密码长度不能超过32位');
                return false;
            }
            return true;
        }
    };

    window.ValidatorUtil = ValidatorUtil;
    window.validator = ValidatorUtil;
})();
