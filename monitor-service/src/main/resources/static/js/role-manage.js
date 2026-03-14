/**
 * 角色管理页面 JavaScript
 */

let allMenus = [];
let isEdit = false;

document.addEventListener('DOMContentLoaded', function() {
    loadRoles();
    loadMenus();
});

async function loadRoles() {
    try {
        const roleName = document.getElementById('searchRoleName').value;
        const status = document.getElementById('searchStatus').value;
        
        const params = {};
        if (roleName) params.roleName = roleName;
        if (status !== '') params.status = status;
        
        const roles = await http.get('/system/role/list', params);
        renderRoleTable(roles);
    } catch (error) {
        console.error('加载角色列表失败:', error);
        document.getElementById('roleTableBody').innerHTML = 
            '<tr><td colspan="7" class="text-center text-danger">加载失败</td></tr>';
    }
}

async function loadMenus() {
    try {
        allMenus = await http.get('/system/menu/all');
    } catch (error) {
        console.error('加载菜单列表失败:', error);
    }
}

function renderRoleTable(roles) {
    const tbody = document.getElementById('roleTableBody');
    
    if (!roles || roles.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = roles.map(role => `
        <tr>
            <td>${role.id}</td>
            <td>${role.roleName || '-'}</td>
            <td><code>${role.roleCode || '-'}</code></td>
            <td>${role.roleDesc || '-'}</td>
            <td>${renderStatus(role.status)}</td>
            <td>${dateFormat.format(role.createTime)}</td>
            <td class="action-btns">
                <button class="btn btn-primary btn-sm" onclick="editRole(${role.id})">编辑</button>
                ${role.roleCode !== 'SUPER_ADMIN' ? 
                    `<button class="btn btn-danger btn-sm" onclick="deleteRole(${role.id})">删除</button>` : 
                    ''
                }
            </td>
        </tr>
    `).join('');
}

function renderStatus(status) {
    const statusMap = {
        0: '<span class="tag success">正常</span>',
        1: '<span class="tag danger">禁用</span>'
    };
    return statusMap[status] || '<span class="tag">未知</span>';
}

function searchRoles() {
    loadRoles();
}

function resetSearch() {
    document.getElementById('searchRoleName').value = '';
    document.getElementById('searchStatus').value = '';
    loadRoles();
}

function showAddModal() {
    isEdit = false;
    document.getElementById('modalTitle').textContent = '新增角色';
    document.getElementById('roleForm').reset();
    document.getElementById('roleId').value = '';
    document.getElementById('formRoleCode').readOnly = false;
    renderMenuTree([]);
    document.getElementById('roleModal').style.display = 'flex';
}

async function editRole(id) {
    try {
        const data = await http.get(`/system/role/${id}`);
        isEdit = true;
        document.getElementById('modalTitle').textContent = '编辑角色';
        document.getElementById('roleId').value = id;
        document.getElementById('formRoleName').value = data.role.roleName || '';
        document.getElementById('formRoleCode').value = data.role.roleCode || '';
        document.getElementById('formRoleCode').readOnly = true;
        document.getElementById('formRoleDesc').value = data.role.roleDesc || '';
        document.getElementById('formRemark').value = data.role.remark || '';
        renderMenuTree(data.menuIds || []);
        document.getElementById('roleModal').style.display = 'flex';
    } catch (error) {
        console.error('获取角色信息失败:', error);
        message.error('获取角色信息失败');
    }
}

function renderMenuTree(selectedMenuIds) {
    const container = document.getElementById('menuTree');
    
    const menuTree = buildMenuTree(allMenus, 0);
    
    container.innerHTML = menuTree.map(menu => renderMenuItem(menu, selectedMenuIds)).join('');
}

function buildMenuTree(menus, parentId) {
    return menus
        .filter(m => m.parentId === parentId)
        .map(m => ({
            ...m,
            children: buildMenuTree(menus, m.id)
        }));
}

function renderMenuItem(menu, selectedMenuIds) {
    const checked = selectedMenuIds.includes(menu.id) ? 'checked' : '';
    const hasChildren = menu.children && menu.children.length > 0;
    
    let html = `
        <div class="menu-item">
            <label class="checkbox-label">
                <input type="checkbox" value="${menu.id}" ${checked} onchange="handleMenuCheck(this, ${menu.id})">
                ${menu.menuName}
            </label>
    `;
    
    if (hasChildren) {
        html += `<div class="menu-children">${menu.children.map(child => renderMenuItem(child, selectedMenuIds)).join('')}</div>`;
    }
    
    html += '</div>';
    return html;
}

function handleMenuCheck(checkbox, menuId) {
}

function closeModal() {
    document.getElementById('roleModal').style.display = 'none';
}

async function saveRole() {
    const id = document.getElementById('roleId').value;
    const roleName = document.getElementById('formRoleName').value.trim();
    const roleCode = document.getElementById('formRoleCode').value.trim();
    const roleDesc = document.getElementById('formRoleDesc').value.trim();
    const remark = document.getElementById('formRemark').value.trim();
    
    if (!roleName) {
        message.error('请输入角色名称');
        return;
    }
    
    if (!roleCode) {
        message.error('请输入角色编码');
        return;
    }
    
    const menuCheckboxes = document.querySelectorAll('#menuTree input:checked');
    const menuIds = Array.from(menuCheckboxes).map(cb => parseInt(cb.value));
    
    const params = {
        roleName,
        roleCode,
        roleDesc,
        remark,
        menuIds
    };
    
    try {
        if (isEdit) {
            params.id = id;
            await http.put('/system/role/update', params);
            message.success('更新成功');
        } else {
            await http.post('/system/role/add', params);
            message.success('创建成功');
        }
        closeModal();
        loadRoles();
    } catch (error) {
        console.error('保存角色失败:', error);
        message.error(error.message || '保存失败');
    }
}

async function deleteRole(id) {
    if (!confirm('确定要删除该角色吗？此操作不可恢复！')) {
        return;
    }
    
    try {
        await http.delete(`/system/role/${id}`);
        message.success('删除成功');
        loadRoles();
    } catch (error) {
        console.error('删除失败:', error);
        message.error('删除失败');
    }
}

document.getElementById('roleModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeModal();
    }
});
