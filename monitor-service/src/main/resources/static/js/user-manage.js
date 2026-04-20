let userTable;
let allRoles = [];
let isEdit = false;

document.addEventListener('DOMContentLoaded', function() {
    initUserTable();
    loadRoles();
});

function initUserTable() {
    userTable = TableUtils.createInstance({
        instanceName: 'userTable',
        apiUrl: '/system/user/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'userTableBody',
        paginationEl: 'pagination',
        colspan: 8,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(user) {
            const cell = TableUtils.cell;
            
            const buttons = [
                { text: '编辑', type: 'primary', onClick: `editUser(${user.id})` },
                { text: '重置密码', type: 'warning', onClick: `showResetPwdModal(${user.id})` }
            ];
            
            if (user.status === 0) {
                buttons.push({ text: '禁用', type: 'default', onClick: `changeStatus(${user.id}, 1)` });
            } else if (user.status === 1) {
                buttons.push({ text: '启用', type: 'success', onClick: `changeStatus(${user.id}, 0)` });
            } else {
                buttons.push({ text: '解锁', type: 'success', onClick: `changeStatus(${user.id}, 0)` });
            }
            
            buttons.push({ text: '删除', type: 'danger', onClick: `deleteUser(${user.id})` });
            
            return `
                <tr>
                    <td>${user.id || '-'}</td>
                    ${cell.renderCell(user.username, { maxLength: 20 })}
                    ${cell.renderCell(user.nickname, { maxLength: 20 })}
                    ${cell.renderCell(user.phone, { maxLength: 20 })}
                    ${cell.renderCell(user.email, { maxLength: 30 })}
                    <td>${renderStatus(user.status)}</td>
                    <td>${dateFormat.format(user.createTime)}</td>
                    ${cell.renderActionCell(buttons)}
                </tr>
            `;
        }
    });
    
    window.userTable = userTable;
    userTable.loadData();
}

function renderStatusButton(user) {
    if (user.status === 0) {
        return TableUtils.cell.renderButton('禁用', 'default', `changeStatus(${user.id}, 1)`);
    } else if (user.status === 1) {
        return TableUtils.cell.renderButton('启用', 'success', `changeStatus(${user.id}, 0)`);
    } else {
        return TableUtils.cell.renderButton('解锁', 'success', `changeStatus(${user.id}, 0)`);
    }
}

async function loadRoles() {
    try {
        const result = await http.get('/system/role/list', { status: 0 });
        allRoles = result.list || result || [];
    } catch (error) {
        console.error('加载角色列表失败:', error);
        allRoles = [];
    }
}

function renderStatus(status) {
    const statusMap = {
        0: '<span class="tag success">正常</span>',
        1: '<span class="tag danger">禁用</span>',
        2: '<span class="tag warning">锁定</span>'
    };
    return statusMap[status] || '<span class="tag">未知</span>';
}

function searchUsers() {
    const username = userTable.getSearchValue('searchUsername');
    const status = userTable.getSearchSelectValue('searchStatus');
    
    const params = {};
    if (username) params.username = username;
    if (status !== '') params.status = status;
    
    userTable.search(params);
}

function resetSearch() {
    document.getElementById('searchUsername').value = '';
    document.getElementById('searchStatus').value = '';
    userTable.resetSearch();
}

function showAddModal() {
    isEdit = false;
    document.getElementById('modalTitle').textContent = '新增用户';
    document.getElementById('userForm').reset();
    document.getElementById('userId').value = '';
    document.getElementById('passwordGroup').style.display = 'block';
    document.getElementById('formPassword').required = true;
    renderRoleCheckboxes([]);
    document.getElementById('userModal').style.display = 'flex';
}

async function editUser(id) {
    try {
        const data = await http.get(`/system/user/${id}`);
        isEdit = true;
        document.getElementById('modalTitle').textContent = '编辑用户';
        document.getElementById('userId').value = id;
        document.getElementById('formUsername').value = data.user.username;
        document.getElementById('formUsername').readOnly = true;
        document.getElementById('formNickname').value = data.user.nickname || '';
        document.getElementById('formPhone').value = data.user.phone || '';
        document.getElementById('formEmail').value = data.user.email || '';
        document.getElementById('formRemark').value = data.user.remark || '';
        document.getElementById('passwordGroup').style.display = 'none';
        document.getElementById('formPassword').required = false;
        renderRoleCheckboxes(data.roleIds || []);
        document.getElementById('userModal').style.display = 'flex';
    } catch (error) {
        console.error('获取用户信息失败:', error);
        message.error('获取用户信息失败');
    }
}

function renderRoleCheckboxes(selectedRoleIds) {
    const container = document.getElementById('roleCheckboxes');
    container.innerHTML = allRoles.map(role => `
        <label class="checkbox-label">
            <input type="checkbox" value="${role.id}" 
                   ${selectedRoleIds.includes(role.id) ? 'checked' : ''}>
            ${role.roleName}
        </label>
    `).join('');
}

function closeModal() {
    document.getElementById('userModal').style.display = 'none';
    document.getElementById('formUsername').readOnly = false;
}

async function saveUser() {
    const id = document.getElementById('userId').value;
    const username = document.getElementById('formUsername').value.trim();
    const password = document.getElementById('formPassword').value;
    const nickname = document.getElementById('formNickname').value.trim();
    const phone = document.getElementById('formPhone').value.trim();
    const email = document.getElementById('formEmail').value.trim();
    const remark = document.getElementById('formRemark').value.trim();
    
    if (!username) {
        message.error('请输入用户名');
        return;
    }
    
    if (!isEdit && !password) {
        message.error('请输入密码');
        return;
    }
    
    const roleCheckboxes = document.querySelectorAll('#roleCheckboxes input:checked');
    const roleIds = Array.from(roleCheckboxes).map(cb => parseInt(cb.value));
    
    const params = {
        username,
        nickname,
        phone,
        email,
        remark,
        roleIds
    };
    
    if (!isEdit) {
        params.password = password;
    }
    
    try {
        if (isEdit) {
            params.id = id;
            await http.put('/system/user/update', params);
            message.success('更新成功');
        } else {
            await http.post('/system/user/add', params);
            message.success('创建成功');
        }
        closeModal();
        userTable.loadData();
    } catch (error) {
        console.error('保存用户失败:', error);
        message.error(error.message || '保存失败');
    }
}

async function changeStatus(id, status) {
    const action = status === 0 ? '启用' : (status === 1 ? '禁用' : '解锁');
    
    if (!confirm(`确定要${action}该用户吗？`)) {
        return;
    }
    
    try {
        await http.put(`/system/user/status/${id}?status=${status}`);
        message.success(`${action}成功`);
        userTable.loadData();
    } catch (error) {
        console.error('操作失败:', error);
        message.error('操作失败');
    }
}

async function deleteUser(id) {
    if (!confirm('确定要删除该用户吗？此操作不可恢复！')) {
        return;
    }
    
    try {
        await http.delete(`/system/user/${id}`);
        message.success('删除成功');
        userTable.loadData();
    } catch (error) {
        console.error('删除失败:', error);
        message.error('删除失败');
    }
}

function showResetPwdModal(id) {
    document.getElementById('resetUserId').value = id;
    document.getElementById('newPassword').value = '';
    document.getElementById('confirmPassword').value = '';
    document.getElementById('resetPwdModal').style.display = 'flex';
}

function closeResetPwdModal() {
    document.getElementById('resetPwdModal').style.display = 'none';
}

async function confirmResetPwd() {
    const id = document.getElementById('resetUserId').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    
    if (!newPassword) {
        message.error('请输入新密码');
        return;
    }
    
    if (newPassword !== confirmPassword) {
        message.error('两次输入的密码不一致');
        return;
    }
    
    if (newPassword.length < 6) {
        message.error('密码长度不能少于6位');
        return;
    }
    
    try {
        await http.put(`/system/user/resetPwd/${id}?newPassword=${encodeURIComponent(newPassword)}`);
        message.success('密码重置成功');
        closeResetPwdModal();
    } catch (error) {
        console.error('重置密码失败:', error);
        message.error('重置密码失败');
    }
}

document.getElementById('userModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeModal();
    }
});

document.getElementById('resetPwdModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeResetPwdModal();
    }
});
