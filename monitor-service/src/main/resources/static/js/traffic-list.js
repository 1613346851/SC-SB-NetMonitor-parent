/**
 * 流量列表页面 JavaScript
 * 使用 TableUtils 通用组件
 */

let trafficTable;

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    
    initTrafficTable();
});

function initTrafficTable() {
    trafficTable = TableUtils.createInstance({
        instanceName: 'trafficTable',
        apiUrl: '/traffic/list',
        pageSize: 10,
        defaultSortField: 'id',
        defaultSortOrder: 'desc',
        tableBodyEl: 'trafficTableBody',
        paginationEl: 'pagination',
        colspan: 12,
        renderRow: function(item) {
            return `
                <tr>
                    <td><a href="javascript:void(0)" onclick="trafficTable.sort('id')">${item.id || '-'}</a></td>
                    <td>${item.trafficId || '-'}</td>
                    <td><a href="javascript:void(0)" onclick="trafficTable.sort('requestTime')">${dateFormat.format(item.requestTime)}</a></td>
                    <td>${item.sourceIp || '-'}</td>
                    <td>${item.targetIp || '-'}</td>
                    <td>${item.sourcePort || '-'}</td>
                    <td>${item.targetPort || '-'}</td>
                    <td><span class="badge badge-${getHttpMethodBadge(item.httpMethod)}">${item.httpMethod || '-'}</span></td>
                    <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis;" title="${item.requestUri || ''}">${item.requestUri || '-'}</td>
                    <td><span class="badge badge-${getStatusBadge(item.responseStatus)}">${item.responseStatus || '-'}</span></td>
                    <td>${item.responseTime || '-'}</td>
                    <td>
                        <button class="btn btn-primary btn-sm" onclick="viewTrafficDetail(${item.id})">详情</button>
                    </td>
                </tr>
            `;
        }
    });
    
    window.trafficTable = trafficTable;
    trafficTable.loadData();
}

function getHttpMethodBadge(method) {
    const badges = {
        'GET': 'success',
        'POST': 'primary',
        'PUT': 'warning',
        'DELETE': 'danger',
        'PATCH': 'info'
    };
    return badges[method] || 'default';
}

function getStatusBadge(status) {
    if (!status) return 'default';
    if (status >= 200 && status < 300) return 'success';
    if (status >= 300 && status < 400) return 'info';
    if (status >= 400 && status < 500) return 'warning';
    if (status >= 500) return 'danger';
    return 'default';
}

function searchTraffic() {
    const sourceIp = trafficTable.getSearchValue('sourceIp');
    const targetIp = trafficTable.getSearchValue('targetIp');
    const httpMethod = trafficTable.getSearchSelectValue('httpMethod');
    const dateRange = trafficTable.getDateRangeValue('startDate', 'endDate');
    
    const params = {};
    if (sourceIp) params.sourceIp = sourceIp;
    if (targetIp) params.targetIp = targetIp;
    if (httpMethod) params.httpMethod = httpMethod;
    if (dateRange.startTime) params.startTime = dateRange.startTime;
    if (dateRange.endTime) params.endTime = dateRange.endTime;
    
    trafficTable.search(params);
}

function resetSearch() {
    document.getElementById('sourceIp').value = '';
    document.getElementById('targetIp').value = '';
    document.getElementById('httpMethod').value = '';
    document.getElementById('startDate').value = dateFormat.daysAgo(7);
    document.getElementById('endDate').value = new Date().toISOString().split('T')[0];
    
    trafficTable.resetSearch();
}

function exportTraffic() {
    trafficTable.exportCSV('/traffic/export', 'traffic_export.csv');
}

async function viewTrafficDetail(id) {
    try {
        const detail = await http.get(`/traffic/${id}`);
        
        const content = document.getElementById('trafficDetailContent');
        content.innerHTML = `
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div>
                    <p><strong>ID:</strong> ${detail.id}</p>
                    <p><strong>流量 ID:</strong> ${detail.trafficId || '-'}</p>
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
