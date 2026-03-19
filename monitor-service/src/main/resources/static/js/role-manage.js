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
    renderMenuTree([], true);
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

function renderMenuTree(selectedMenuIds, collapsed = false) {
    const container = document.getElementById('menuTree');
    
    const menuTree = buildMenuTree(allMenus, 0);
    
    container.innerHTML = menuTree.map(menu => renderMenuItem(menu, selectedMenuIds, 0, collapsed)).join('');
}

function buildMenuTree(menus, parentId) {
    return menus
        .filter(m => m.parentId === parentId)
        .map(m => ({
            ...m,
            children: buildMenuTree(menus, m.id)
        }));
}

function renderMenuItem(menu, selectedMenuIds, level = 0, defaultCollapsed = false) {
    const checked = selectedMenuIds.includes(menu.id) ? 'checked' : '';
    const hasChildren = menu.children && menu.children.length > 0;
    const menuId = `menu-${menu.id}`;
    const isExpanded = hasChildren && !defaultCollapsed ? 'expanded' : '';
    const childrenDisplay = defaultCollapsed ? 'none' : 'block';
    
    let html = `
        <div class="menu-tree-node ${level === 0 ? 'root-node' : ''}">
            <div class="menu-tree-node-content">
                ${hasChildren ? `
                    <span class="menu-toggle ${isExpanded}" onclick="toggleMenuNode(this)">
                        <svg viewBox="0 0 24 24" width="14" height="14">
                            <path fill="currentColor" d="M7 10l5 5 5-5z"/>
                        </svg>
                    </span>
                ` : '<span class="menu-toggle-placeholder"></span>'}
                <label class="checkbox-label">
                    <input type="checkbox" value="${menu.id}" ${checked} onchange="handleMenuCheck(this, ${menu.id})">
                    <span class="menu-name">${menu.menuName}</span>
                </label>
            </div>
    `;
    
    if (hasChildren) {
        html += `<div class="menu-children" style="display: ${childrenDisplay};">${menu.children.map(child => renderMenuItem(child, selectedMenuIds, level + 1, defaultCollapsed)).join('')}</div>`;
    }
    
    html += '</div>';
    return html;
}

function toggleMenuNode(toggleBtn) {
    const node = toggleBtn.closest('.menu-tree-node');
    const children = node.querySelector('.menu-children');
    if (children) {
        const isExpanded = toggleBtn.classList.contains('expanded');
        toggleBtn.classList.toggle('expanded');
        children.style.display = isExpanded ? 'none' : 'block';
    }
}

function handleMenuCheck(checkbox, menuId) {
    const node = checkbox.closest('.menu-tree-node');
    const children = node.querySelectorAll('.menu-children input[type="checkbox"]');
    children.forEach(child => {
        child.checked = checkbox.checked;
    });
    
    if (checkbox.checked) {
        let parent = node.parentElement;
        while (parent) {
            if (parent.classList.contains('menu-tree-node')) {
                const parentCheckbox = parent.querySelector(':scope > .menu-tree-node-content input[type="checkbox"]');
                if (parentCheckbox && !parentCheckbox.checked) {
                    parentCheckbox.checked = true;
                }
            }
            parent = parent.parentElement;
        }
    } else {
        updateParentCheckboxState(node);
    }
}

function updateParentCheckboxState(node) {
    let parent = node.parentElement;
    while (parent) {
        if (parent.classList.contains('menu-tree-node')) {
            const parentCheckbox = parent.querySelector(':scope > .menu-tree-node-content input[type="checkbox"]');
            const siblingCheckboxes = parent.querySelectorAll(':scope > .menu-children > .menu-tree-node > .menu-tree-node-content input[type="checkbox"]');
            if (parentCheckbox && siblingCheckboxes.length > 0) {
                const allUnchecked = Array.from(siblingCheckboxes).every(cb => !cb.checked);
                if (allUnchecked) {
                    parentCheckbox.checked = false;
                }
            }
        }
        parent = parent.parentElement;
    }
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
