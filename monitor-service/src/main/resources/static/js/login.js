/**
 * 网络监测系统 - 登录页面 JavaScript
 * 处理用户登录、表单验证、登录状态管理
 */

(function() {
    'use strict';
    
    if (AuthService.isAuthenticated()) {
        const returnUrl = getReturnUrl();
        window.location.href = returnUrl || '/';
        return;
    }
    
    document.addEventListener('DOMContentLoaded', function() {
        const rememberedUsername = StorageUtil.get('remembered_username');
        if (rememberedUsername) {
            document.getElementById('username').value = rememberedUsername;
            document.getElementById('rememberMe').checked = true;
        }
        
        document.getElementById('username').focus();
    });
})();

function getReturnUrl() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('returnUrl');
}

function togglePassword() {
    const passwordInput = document.getElementById('password');
    const toggleBtn = document.querySelector('.password-toggle');
    
    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
        toggleBtn.textContent = '🙈';
    } else {
        passwordInput.type = 'password';
        toggleBtn.textContent = '👁️';
    }
}

function showError(message) {
    const errorEl = document.getElementById('loginError');
    errorEl.textContent = message;
    errorEl.classList.add('show');
    
    const loginBox = document.querySelector('.login-box');
    loginBox.classList.add('animate-shake');
    setTimeout(() => loginBox.classList.remove('animate-shake'), 500);
}

function hideError() {
    const errorEl = document.getElementById('loginError');
    errorEl.classList.remove('show');
}

function setLoading(loading) {
    const loginBtn = document.getElementById('loginBtn');
    const form = document.getElementById('loginForm');
    
    if (loading) {
        loginBtn.classList.add('btn-loading');
        loginBtn.disabled = true;
        loginBtn.textContent = '登录中...';
        form.style.pointerEvents = 'none';
    } else {
        loginBtn.classList.remove('btn-loading');
        loginBtn.disabled = false;
        loginBtn.textContent = '登 录';
        form.style.pointerEvents = 'auto';
    }
}

async function handleLogin(event) {
    event.preventDefault();
    hideError();
    
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const rememberMe = document.getElementById('rememberMe').checked;
    
    if (!username) {
        showError('请输入用户名');
        document.getElementById('username').focus();
        return false;
    }
    
    if (!password) {
        showError('请输入密码');
        document.getElementById('password').focus();
        return false;
    }
    
    setLoading(true);
    
    try {
        const result = await AuthService.login(username, password);
        
        if (result.success) {
            if (rememberMe) {
                StorageUtil.set('remembered_username', username);
            } else {
                StorageUtil.remove('remembered_username');
            }
            
            MessageUtil.success('登录成功，正在跳转...');
            
            setTimeout(() => {
                const returnUrl = getReturnUrl();
                window.location.href = returnUrl || '/';
            }, 500);
        } else {
            showError(result.message || '登录失败，请检查用户名和密码');
        }
    } catch (error) {
        console.error('Login error:', error);
        showError(error.message || '登录失败，请稍后重试');
    } finally {
        setLoading(false);
    }
    
    return false;
}

document.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && e.target.tagName !== 'BUTTON') {
        const loginBtn = document.getElementById('loginBtn');
        if (!loginBtn.disabled) {
            loginBtn.click();
        }
    }
});
