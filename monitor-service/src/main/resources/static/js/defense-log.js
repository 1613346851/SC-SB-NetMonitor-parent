/**
 * 防御日志页面 JavaScript
 * 负责防御日志数据加载、筛选、分页等功能
 */

let currentPage = 1;
const pageSize = 10;
let searchParams = {};

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    loadDefenseData();
});

async function loadDefenseData() {
    try {
        const params = {
            page: currentPage,
            size: pageSize,
            ...searchParams
        };

        const result = await http.get('/defense/list', params);
        
        renderDefenseTable(result.records || []);
        renderPagination(result.total || 0);
    } catch (error) {
        console.error('加载防御日志失败:', error);
    }
}

function renderDefenseTable(data) {
    const tbody = document.getElementById('defenseTableBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr>
            <td>${item.id || '-'}</td>
            <td>${dateFormat.format(item.defenseTime)}</td>
            <td>${item.sourceIp || '-'}</td>
            <td>${renderDefenseType(item.defenseType)}</td>
            <td>${item.attackId || '-'}</td>
            <td>${item.success ? '<span class="tag success">成功</span>' : '<span class="tag danger">失败</span>'}</td>
            <td>${item.operator || '-'}</td>
            <td>${item.remark || '-'}</td>
        </tr>
    `).join('');
}

function renderDefenseType(type) {
    const typeMap = {
        'BLOCK_IP': { text: '封禁 IP', class: 'danger' },
        'RATE_LIMIT': { text: '限流', class: 'warning' },
        'BLOCK_REQUEST': { text: '拦截请求', class: 'primary' },
    };
    
    const config = typeMap[type] || { text: type, class: 'info' };
    return `<span class="tag ${config.class}">${config.text}</span>`;
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    const paginationEl = document.getElementById('pagination');
    
    let html = `
        <span class="pagination-item ${currentPage === 1 ? 'disabled' : ''}" 
              onclick="goPage(${currentPage - 1})">上一页</span>
    `;
    
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= currentPage - 1 && i <= currentPage + 1)) {
            html += `
                <span class="pagination-item ${i === currentPage ? 'active' : ''}" 
                      onclick="goPage(${i})">${i}</span>
            `;
        } else if (i === currentPage - 2 || i === currentPage + 2) {
            html += `<span class="pagination-item">...</span>`;
        }
    }
    
    html += `
        <span class="pagination-item ${currentPage === totalPages ? 'disabled' : ''}" 
              onclick="goPage(${currentPage + 1})">下一页</span>
        <span class="pagination-item">共 ${total} 条</span>
    `;
    
    paginationEl.innerHTML = html;
}

function goPage(page) {
    const totalPages = Math.ceil((searchParams.total || 10) / pageSize);
    
    if (page < 1 || page > totalPages || page === currentPage) {
        return;
    }
    
    currentPage = page;
    loadDefenseData();
}

function searchDefenseLogs() {
    const sourceIp = document.getElementById('sourceIp').value.trim();
    const defenseType = document.getElementById('defenseType').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    searchParams = {};
    
    if (sourceIp) searchParams.sourceIp = sourceIp;
    if (defenseType) searchParams.defenseType = defenseType;
    if (startDate) searchParams.startDate = startDate + ' 00:00:00';
    if (endDate) searchParams.endDate = endDate + ' 23:59:59';
    
    currentPage = 1;
    loadDefenseData();
}

function resetSearch() {
    document.getElementById('sourceIp').value = '';
    document.getElementById('defenseType').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    searchParams = {};
    currentPage = 1;
    loadDefenseData();
}
