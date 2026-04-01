let traceTable;

document.addEventListener('DOMContentLoaded', function() {
    initTraceTable();
    quickSearch('today', null);
});

function initTraceTable() {
    traceTable = TableUtils.createInstance({
        instanceName: 'traceTable',
        apiUrl: '/trace/search',
        pageSize: 20,
        defaultSortField: 'createTime',
        defaultSortOrder: 'desc',
        tableBodyEl: 'traceTableBody',
        paginationEl: 'pagination',
        colspan: 7,
        fixedAction: true,
        enableTooltip: true,
        renderRow: function(item) {
            const cell = TableUtils.cell;
            
            return `
                <tr>
                    <td>${dateFormat.format(item.createTime)}</td>
                    <td><a class="link" href="/ip-profile?ip=${item.sourceIp}">${item.sourceIp}</a></td>
                    <td>${cell.renderAttackType(item.attackType)}</td>
                    ${cell.renderCell(item.targetUri, { maxLength: 40 })}
                    <td>${cell.renderRiskLevel(item.riskLevel)}</td>
                    <td>${cell.renderStatus(item.handled, 'handle')}</td>
                    ${cell.renderActionCell([
                        { text: '查看画像', type: 'primary', onClick: `viewProfile('${item.sourceIp}')` }
                    ])}
                </tr>
            `;
        }
    });
    
    window.traceTable = traceTable;
}

function searchTraces() {
    const sourceIp = traceTable.getSearchValue('searchIp');
    const attackType = traceTable.getSearchSelectValue('attackType');
    const riskLevel = traceTable.getSearchSelectValue('riskLevel');
    const startTime = traceTable.getSearchValue('startTime');
    const endTime = traceTable.getSearchValue('endTime');
    
    const params = {};
    if (sourceIp) params.ip = sourceIp;
    if (attackType) params.attackType = attackType;
    if (riskLevel) params.riskLevel = riskLevel;
    if (startTime) params.startTime = startTime.replace('T', ' ') + ':00';
    if (endTime) params.endTime = endTime.replace('T', ' ') + ':00';
    
    traceTable.search(params);
}

function resetSearch() {
    document.getElementById('searchIp').value = '';
    document.getElementById('attackType').value = '';
    document.getElementById('riskLevel').value = '';
    document.getElementById('startTime').value = '';
    document.getElementById('endTime').value = '';
    
    document.querySelectorAll('.quick-btn').forEach(function(btn) {
        btn.classList.remove('active');
    });
    
    traceTable.resetSearch();
}

function quickSearch(type, evt) {
    const now = new Date();
    let startTime, endTime;
    
    function formatDateTime(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return year + '-' + month + '-' + day + 'T' + hours + ':' + minutes;
    }
    
    endTime = formatDateTime(now);
    
    if (type === 'today') {
        const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        startTime = formatDateTime(todayStart);
    } else if (type === 'yesterday') {
        const yesterdayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 0, 0, 0);
        const yesterdayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        startTime = formatDateTime(yesterdayStart);
        endTime = formatDateTime(yesterdayEnd);
    } else if (type === 'week') {
        const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        startTime = formatDateTime(weekAgo);
    } else if (type === 'month') {
        const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        startTime = formatDateTime(monthAgo);
    }
    
    document.getElementById('startTime').value = startTime;
    document.getElementById('endTime').value = endTime;
    
    document.querySelectorAll('.quick-btn').forEach(function(btn) {
        btn.classList.remove('active');
    });
    
    if (evt && evt.target) {
        evt.target.classList.add('active');
    } else {
        const activeBtn = document.querySelector('.quick-btn[onclick*="' + type + '"]');
        if (activeBtn) {
            activeBtn.classList.add('active');
        }
    }
    
    searchTraces();
}

function viewProfile(ip) {
    window.location.href = '/ip-profile?ip=' + ip;
}
