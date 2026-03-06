/**
 * 流量列表页面 JavaScript
 * 负责流量数据加载、筛选、分页、详情查看等功能
 */

let currentPage = 1;
const pageSize = 10;
let searchParams = {};

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    loadTrafficData();
});

async function loadTrafficData() {
    try {
        const params = {
            page: currentPage,
            size: pageSize,
            ...searchParams
        };

        const result = await http.get('/traffic/list', params);
        
        renderTrafficTable(result.records || []);
        renderPagination(result.total || 0);
    } catch (error) {
        console.error('加载流量数据失败:', error);
    }
}

function renderTrafficTable(data) {
    const tbody = document.getElementById('trafficTableBody');
    
    if (!data || data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="11" class="text-center">暂无数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr>
            <td>${item.id || '-'}</td>
            <td>${dateFormat.format(item.requestTime)}</td>
            <td>${item.sourceIp || '-'}</td>
            <td>${item.targetIp || '-'}</td>
            <td>${item.sourcePort || '-'}</td>
            <td>${item.targetPort || '-'}</td>
            <td>${item.httpMethod || '-'}</td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis;">${item.requestUri || '-'}</td>
            <td>${item.responseStatus || '-'}</td>
            <td>${item.responseTime || '-'}</td>
            <td>
                <button class="btn btn-primary btn-sm" onclick="viewTrafficDetail(${item.id})">详情</button>
            </td>
        </tr>
    `).join('');
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
    loadTrafficData();
}

function searchTraffic() {
    const sourceIp = document.getElementById('sourceIp').value.trim();
    const targetIp = document.getElementById('targetIp').value.trim();
    const httpMethod = document.getElementById('httpMethod').value;
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    
    searchParams = {};
    
    if (sourceIp) searchParams.sourceIp = sourceIp;
    if (targetIp) searchParams.targetIp = targetIp;
    if (httpMethod) searchParams.httpMethod = httpMethod;
    if (startDate) searchParams.startDate = startDate + ' 00:00:00';
    if (endDate) searchParams.endDate = endDate + ' 23:59:59';
    
    currentPage = 1;
    loadTrafficData();
}

function resetSearch() {
    document.getElementById('sourceIp').value = '';
    document.getElementById('targetIp').value = '';
    document.getElementById('httpMethod').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    searchParams = {};
    currentPage = 1;
    loadTrafficData();
}

function exportTraffic() {
    const params = new URLSearchParams({
        ...searchParams,
        export: 'true'
    });
    
    window.location.href = `/api/traffic/export?${params.toString()}`;
}

async function viewTrafficDetail(id) {
    try {
        const detail = await http.get(`/traffic/${id}`);
        
        const content = document.getElementById('trafficDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
                    <p><strong>请求时间:</strong> ${dateFormat.format(detail.requestTime)}</p>
                    <p><strong>源 IP:</strong> ${detail.sourceIp}</p>
                    <p><strong>源端口:</strong> ${detail.sourcePort}</p>
                    <p><strong>目标 IP:</strong> ${detail.targetIp}</p>
                    <p><strong>目标端口:</strong> ${detail.targetPort}</p>
                    <p><strong>HTTP 方法:</strong> ${detail.httpMethod}</p>
                    <p><strong>协议:</strong> ${detail.protocol || 'HTTP/1.1'}</p>
                </div>
                <div>
                    <p><strong>请求 URI:</strong> ${detail.requestUri}</p>
                    <p><strong>查询参数:</strong> ${detail.queryParams || '-'}</p>
                    <p><strong>Content-Type:</strong> ${detail.contentType || '-'}</p>
                    <p><strong>响应状态:</strong> ${detail.responseStatus}</p>
                    <p><strong>响应时间:</strong> ${detail.responseTime} ms</p>
                    <p><strong>User-Agent:</strong> ${detail.userAgent || '-'}</p>
                </div>
            </div>
            <div class="mt-24">
                <p><strong>请求头:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 200px; overflow-y: auto;">${formatHeaders(detail.requestHeaders)}</pre>
            </div>
            ${detail.requestBody ? `
            <div class="mt-16">
                <p><strong>请求体:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 300px; overflow-y: auto;">${detail.requestBody}</pre>
            </div>
            ` : ''}
            ${detail.responseBody ? `
            <div class="mt-16">
                <p><strong>响应体:</strong></p>
                <pre style="background: #f5f5f5; padding: 12px; border-radius: 4px; max-height: 300px; overflow-y: auto;">${detail.responseBody}</pre>
            </div>
            ` : ''}
        `;
        
        document.getElementById('trafficDetailModal').style.display = 'flex';
    } catch (error) {
        console.error('加载流量详情失败:', error);
    }
}

function closeTrafficDetail() {
    document.getElementById('trafficDetailModal').style.display = 'none';
}

function formatHeaders(headersStr) {
    if (!headersStr) return '-';
    try {
        const headers = JSON.parse(headersStr);
        return Object.entries(headers)
            .map(([key, value]) => `${key}: ${value}`)
            .join('\n');
    } catch (e) {
        return headersStr;
    }
}

document.getElementById('trafficDetailModal')?.addEventListener('click', function(e) {
    if (e.target === this) {
        closeTrafficDetail();
    }
});
