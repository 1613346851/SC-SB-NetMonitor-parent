let currentPage = 1;
const pageSize = 20;

function init() {
    quickSearch('today');
}

function quickSearch(type) {
    const now = new Date();
    let startTime, endTime;
    
    endTime = now.toISOString().slice(0, 16);
    
    if (type === 'today') {
        startTime = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString().slice(0, 16);
    } else if (type === 'yesterday') {
        startTime = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1).toISOString().slice(0, 16);
        endTime = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString().slice(0, 16);
    } else if (type === 'week') {
        startTime = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16);
    } else if (type === 'month') {
        startTime = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16);
    }
    
    document.getElementById('searchStartTime').value = startTime;
    document.getElementById('searchEndTime').value = endTime;
    
    document.querySelectorAll('.quick-btn').forEach(function(btn) {
        btn.classList.remove('active');
    });
    event.target.classList.add('active');
    
    doSearch();
}

function doSearch() {
    currentPage = 1;
    loadResults();
}

function resetSearch() {
    document.getElementById('searchForm').reset();
    document.querySelectorAll('.quick-btn').forEach(function(btn) {
        btn.classList.remove('active');
    });
    currentPage = 1;
    document.getElementById('resultTableBody').innerHTML = '<tr><td colspan="7" class="text-center">请输入查询条件</td></tr>';
    document.getElementById('resultCount').textContent = '共 0 条记录';
    document.getElementById('pagination').innerHTML = '';
}

function loadResults() {
    const params = buildQueryParams();
    
    http.get('/api/trace/search', params)
        .then(function(response) {
            if (response.code === 200) {
                renderResults(response.data);
            } else {
                message.error(response.message || '查询失败');
            }
        })
        .catch(function(error) {
            console.error('查询失败:', error);
            message.error('查询失败');
        });
}

function buildQueryParams() {
    const params = {
        pageNum: currentPage,
        pageSize: pageSize
    };
    
    const ip = document.getElementById('searchIp').value.trim();
    if (ip) params.ip = ip;
    
    const attackType = document.getElementById('searchAttackType').value;
    if (attackType) params.attackType = attackType;
    
    const riskLevel = document.getElementById('searchRiskLevel').value;
    if (riskLevel) params.riskLevel = riskLevel;
    
    const startTime = document.getElementById('searchStartTime').value;
    if (startTime) params.startTime = startTime.replace('T', ' ') + ':00';
    
    const endTime = document.getElementById('searchEndTime').value;
    if (endTime) params.endTime = endTime.replace('T', ' ') + ':00';
    
    return params;
}

function renderResults(data) {
    document.getElementById('resultCount').textContent = '共 ' + data.total + ' 条记录';
    
    const tbody = document.getElementById('resultTableBody');
    
    if (!data.list || data.list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">暂无数据</td></tr>';
        document.getElementById('pagination').innerHTML = '';
        return;
    }
    
    let html = '';
    data.list.forEach(function(item) {
        html += '<tr>' +
            '<td>' + formatDateTime(item.createTime) + '</td>' +
            '<td><a class="ip-link" href="/ip-profile?ip=' + item.sourceIp + '">' + item.sourceIp + '</a></td>' +
            '<td>' + getAttackTypeName(item.attackType) + '</td>' +
            '<td>' + (item.targetUri || '-') + '</td>' +
            '<td>' + getRiskLevelBadge(item.riskLevel) + '</td>' +
            '<td>' + getHandledBadge(item.handled) + '</td>' +
            '<td>' +
            '<button class="trace-btn" onclick="viewProfile(\'' + item.sourceIp + '\')">查看画像</button>' +
            '</td>' +
            '</tr>';
    });
    
    tbody.innerHTML = html;
    renderPagination(data.total, data.pageNum, data.pageSize);
}

function renderPagination(total, pageNum, pageSize) {
    const container = document.getElementById('pagination');
    const totalPages = Math.ceil(total / pageSize);
    
    if (totalPages <= 1) {
        container.innerHTML = '<span class="page-info">共 ' + total + ' 条</span>';
        return;
    }
    
    let html = '<span class="page-info">共 ' + total + ' 条</span>';
    
    if (pageNum > 1) {
        html += '<button class="page-btn" onclick="goToPage(' + (pageNum - 1) + ')">上一页</button>';
    }
    
    const startPage = Math.max(1, pageNum - 2);
    const endPage = Math.min(totalPages, pageNum + 2);
    
    if (startPage > 1) {
        html += '<button class="page-btn" onclick="goToPage(1)">1</button>';
        if (startPage > 2) html += '<span class="page-ellipsis">...</span>';
    }
    
    for (let i = startPage; i <= endPage; i++) {
        if (i === pageNum) {
            html += '<span class="page-current">' + i + '</span>';
        } else {
            html += '<button class="page-btn" onclick="goToPage(' + i + ')">' + i + '</button>';
        }
    }
    
    if (endPage < totalPages) {
        if (endPage < totalPages - 1) html += '<span class="page-ellipsis">...</span>';
        html += '<button class="page-btn" onclick="goToPage(' + totalPages + ')">' + totalPages + '</button>';
    }
    
    if (pageNum < totalPages) {
        html += '<button class="page-btn" onclick="goToPage(' + (pageNum + 1) + ')">下一页</button>';
    }
    
    container.innerHTML = html;
}

function goToPage(page) {
    currentPage = page;
    loadResults();
}

function viewProfile(ip) {
    window.location.href = '/ip-profile?ip=' + ip;
}

function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hour = String(date.getHours()).padStart(2, '0');
    const minute = String(date.getMinutes()).padStart(2, '0');
    const second = String(date.getSeconds()).padStart(2, '0');
    return year + '-' + month + '-' + day + ' ' + hour + ':' + minute + ':' + second;
}

function getAttackTypeName(type) {
    const types = {
        'DDOS': 'DDoS攻击',
        'SQL_INJECTION': 'SQL注入',
        'XSS': 'XSS攻击',
        'PATH_TRAVERSAL': '路径遍历',
        'COMMAND_INJECTION': '命令注入',
        'RATE_LIMIT': '限流触发'
    };
    return types[type] || type || '未知';
}

function getRiskLevelBadge(level) {
    const badges = {
        'CRITICAL': '<span class="badge badge-danger">严重</span>',
        'HIGH': '<span class="badge badge-warning">高危</span>',
        'MEDIUM': '<span class="badge badge-info">中危</span>',
        'LOW': '<span class="badge badge-success">低危</span>'
    };
    return badges[level] || '<span class="badge">未知</span>';
}

function getHandledBadge(handled) {
    if (handled === 1) {
        return '<span class="badge badge-success">已处理</span>';
    }
    return '<span class="badge badge-warning">未处理</span>';
}

document.addEventListener('DOMContentLoaded', init);
