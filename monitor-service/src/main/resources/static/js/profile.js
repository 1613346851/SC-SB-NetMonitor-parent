/**
 * 个人中心页面 JavaScript
 */

let currentUserId = null;

document.addEventListener('DOMContentLoaded', function() {
    loadProfile();
});

async function loadProfile() {
    try {
        const user = await http.get('/auth/me');
        console.log('用户信息:', user);
        
        if (!user) {
            message.error('获取用户信息失败');
            return;
        }
        
        currentUserId = user.id;
        
        document.getElementById('username').value = user.username || '';
        document.getElementById('nickname').value = user.nickname || '';
        document.getElementById('phone').value = user.phone || '';
        document.getElementById('email').value = user.email || '';
        
        const userAvatar = document.querySelector('.user-avatar');
        if (userAvatar && user.nickname) {
            userAvatar.textContent = user.nickname.charAt(0).toUpperCase();
        }
        
        const userName = document.querySelector('.user-name');
        if (userName) {
            userName.textContent = user.nickname || user.username;
        }
    } catch (error) {
        console.error('加载用户信息失败:', error);
        message.error(error.message || '加载用户信息失败');
    }
}

async function updateProfile() {
    const nickname = document.getElementById('nickname').value.trim();
    const phone = document.getElementById('phone').value.trim();
    const email = document.getElementById('email').value.trim();
    
    if (!nickname) {
        message.error('请输入昵称');
        return;
    }
    
    try {
        const params = {
            id: currentUserId,
            nickname,
            phone,
            email
        };
        
        await http.put('/system/user/update', params);
        message.success('保存成功');
        
        const userAvatar = document.querySelector('.user-avatar');
        if (nickname) {
            userAvatar.textContent = nickname.charAt(0).toUpperCase();
        }
        
        const userName = document.querySelector('.user-name');
        if (userName) {
            userName.textContent = nickname;
        }
    } catch (error) {
        console.error('保存失败:', error);
        message.error(error.message || '保存失败');
    }
}

async function changePassword() {
    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    
    if (!currentPassword) {
        message.error('请输入当前密码');
        return;
    }
    
    if (!newPassword) {
        message.error('请输入新密码');
        return;
    }
    
    if (newPassword.length < 6) {
        message.error('新密码长度不能少于6位');
        return;
    }
    
    if (newPassword !== confirmPassword) {
        message.error('两次输入的密码不一致');
        return;
    }
    
    try {
        await http.post('/auth/changePassword', {
            currentPassword,
            newPassword
        });
        
        message.success('密码修改成功，请重新登录');
        
        setTimeout(() => {
            AuthService.logout();
        }, 1500);
    } catch (error) {
        console.error('修改密码失败:', error);
        message.error(error.message || '修改密码失败');
    }
}
