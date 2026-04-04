let traceTable;
let currentProfileIp = null;
let attackPageNum = 1;
const attackPageSize = 10;

document.addEventListener('DOMContentLoaded', function() {
    initTraceTable();
    quickSearch('all', null);
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
    
    if (type === 'all') {
        startTime = '';
        endTime = '';
    } else if (type === 'today') {
        const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        startTime = formatDateTime(todayStart);
        endTime = formatDateTime(now);
    } else if (type === 'yesterday') {
        const yesterdayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 0, 0, 0);
        const yesterdayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        startTime = formatDateTime(yesterdayStart);
        endTime = formatDateTime(yesterdayEnd);
    } else if (type === 'week') {
        const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
        startTime = formatDateTime(weekAgo);
        endTime = formatDateTime(now);
    } else if (type === 'month') {
        const monthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
        startTime = formatDateTime(monthAgo);
        endTime = formatDateTime(now);
    }
    
    document.getElementById('startTime').value = startTime || '';
    document.getElementById('endTime').value = endTime || '';
    
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
    currentProfileIp = ip;
    attackPageNum = 1;
    
    var modal = document.getElementById('ipProfileModal');
    modal.classList.add('show');
    document.body.style.overflow = 'hidden';
    
    loadIpProfile(ip);
    loadAttackChain(ip);
    loadAttackRecords(ip);
}

function closeIpProfileModal() {
    var modal = document.getElementById('ipProfileModal');
    modal.classList.remove('show');
    document.body.style.overflow = '';
    currentProfileIp = null;
}

function loadIpProfile(ip) {
    http.get('/trace/ip/' + ip)
        .then(function(data) {
            renderProfile(data);
        })
        .catch(function(error) {
            console.error('获取IP画像失败:', error);
            message.error('获取IP画像失败');
        });
}

function renderProfile(profile) {
    document.getElementById('profileIp').textContent = profile.ip;
    
    var location = [];
    if (profile.geoInfo && profile.geoInfo.valid) {
        if (profile.geoInfo.country) location.push(profile.geoInfo.country);
        if (profile.geoInfo.province) location.push(profile.geoInfo.province);
        if (profile.geoInfo.city) location.push(profile.geoInfo.city);
    }
    document.getElementById('profileLocation').textContent = location.length > 0 ? location.join(' · ') : '未知位置';
    
    var statusText = profile.currentStatus || '正常';
    var statusClass = 'status-normal';
    if (profile.isBlacklisted) {
        statusText = '已封禁';
        statusClass = 'status-blacklisted';
    } else if (profile.currentState === 2) {
        statusText = '攻击中';
        statusClass = 'status-attacking';
    } else if (profile.currentState === 1) {
        statusText = '可疑';
        statusClass = 'status-suspicious';
    } else if (profile.currentState === 5) {
        statusText = '冷却中';
        statusClass = 'status-defended';
    }
    document.getElementById('profileStatus').textContent = statusText;
    document.getElementById('profileStatus').className = 'profile-status ' + statusClass;
    
    renderRiskScore(profile.riskScore, profile.riskLevel);
    
    document.getElementById('totalAttackCount').textContent = profile.totalAttackCount || 0;
    document.getElementById('totalBlockCount').textContent = profile.totalBlockCount || 0;
    document.getElementById('totalRequestCount').textContent = profile.totalRequestCount || 0;
    document.getElementById('firstSeen').textContent = formatProfileDateTime(profile.firstSeen);
    document.getElementById('lastSeen').textContent = formatProfileDateTime(profile.lastSeen);
    document.getElementById('currentStatus').textContent = statusText;
    
    if (profile.geoInfo && profile.geoInfo.valid) {
        document.getElementById('geoCountry').textContent = profile.geoInfo.country || '-';
        document.getElementById('geoProvince').textContent = profile.geoInfo.province || '-';
        document.getElementById('geoCity').textContent = profile.geoInfo.city || '-';
        if (profile.geoInfo.latitude && profile.geoInfo.longitude) {
            document.getElementById('geoCoords').textContent = 
                profile.geoInfo.latitude.toFixed(4) + ', ' + profile.geoInfo.longitude.toFixed(4);
        }
        document.getElementById('geoIsp').textContent = profile.geoInfo.isp || '-';
    }
    
    if (profile.isBlacklisted) {
        document.getElementById('blacklistInfo').style.display = 'block';
        document.getElementById('blacklistedAt').textContent = formatProfileDateTime(profile.blacklistedAt);
        document.getElementById('blacklistExpire').textContent = profile.blacklistExpireTime 
            ? formatProfileDateTime(profile.blacklistExpireTime) : '永久封禁';
        document.getElementById('blacklistReason').textContent = profile.blacklistReason || '-';
    } else {
        document.getElementById('blacklistInfo').style.display = 'none';
    }
    
    renderAttackTypeStats(profile.attackTypeStats);
    renderHourlyChart(profile.hourlyStats);
}

function renderRiskScore(score, level) {
    var scoreCircle = document.getElementById('riskScoreCircle');
    var scoreValue = document.getElementById('riskScoreValue');
    var levelText = document.getElementById('riskLevelText');
    var levelDesc = document.getElementById('riskLevelDesc');
    
    scoreValue.textContent = score || 0;
    
    scoreCircle.className = 'risk-score-circle';
    if (level === 'CRITICAL') {
        scoreCircle.classList.add('risk-critical');
        levelText.textContent = '极高风险';
        levelDesc.textContent = '该IP存在严重威胁行为，建议立即封禁';
    } else if (level === 'HIGH') {
        scoreCircle.classList.add('risk-high');
        levelText.textContent = '高风险';
        levelDesc.textContent = '该IP存在多次攻击行为，需要重点关注';
    } else if (level === 'MEDIUM') {
        scoreCircle.classList.add('risk-medium');
        levelText.textContent = '中等风险';
        levelDesc.textContent = '该IP存在可疑行为，建议持续观察';
    } else if (level === 'LOW') {
        scoreCircle.classList.add('risk-low');
        levelText.textContent = '低风险';
        levelDesc.textContent = '该IP历史行为正常，风险较低';
    } else {
        scoreCircle.classList.add('risk-minimal');
        levelText.textContent = '极低风险';
        levelDesc.textContent = '该IP无异常行为记录';
    }
}

function renderAttackTypeStats(stats) {
    var container = document.getElementById('attackTypeList');
    
    if (!stats || stats.length === 0) {
        container.innerHTML = '<li class="no-data">暂无攻击记录</li>';
        return;
    }
    
    var html = '';
    stats.forEach(function(stat) {
        var percentage = stat.percentage || 0;
        html += '<li class="attack-type-item">' +
            '<span style="min-width: 100px;">' + (stat.attackTypeName || stat.attackType) + '</span>' +
            '<div class="attack-type-bar">' +
            '<div class="attack-type-fill" style="width: ' + percentage + '%;"></div>' +
            '</div>' +
            '<span class="attack-type-count">' + stat.count + '</span>' +
            '</li>';
    });
    
    container.innerHTML = html;
}

function renderHourlyChart(hourlyStats) {
    var container = document.getElementById('hourlyChart');
    
    if (!hourlyStats || hourlyStats.length === 0) {
        container.innerHTML = '<div class="no-data">暂无数据</div>';
        return;
    }
    
    var maxCount = Math.max.apply(null, hourlyStats.map(function(s) { return s.count || 0; }));
    if (maxCount < 1) maxCount = 1;
    
    var html = '';
    for (var i = 0; i < 24; i++) {
        var stat = hourlyStats.find(function(s) { return s.hour === i; }) || { hour: i, count: 0 };
        var height = maxCount > 0 ? (stat.count / maxCount * 140) : 0;
        html += '<div class="bar-item" style="height: ' + Math.max(height, 4) + 'px;" title="' + i + '时: ' + stat.count + '次">' +
            '<span class="bar-label">' + i + '</span>' +
            '</div>';
    }
    
    container.innerHTML = html;
}

function loadAttackChain(ip) {
    http.get('/trace/chain/' + ip, { limit: 50 })
        .then(function(data) {
            if (data && data.timeline) {
                renderTimeline(data.timeline);
            }
        })
        .catch(function(error) {
            console.error('获取攻击链失败:', error);
        });
}

function renderTimeline(timeline) {
    var container = document.getElementById('timeline');
    
    if (!timeline || timeline.length === 0) {
        container.innerHTML = '<div class="no-data">暂无攻击事件</div>';
        return;
    }
    
    var html = '<div class="timeline-inner">';
    timeline.forEach(function(event) {
        var dotClass = event.eventType === 'ATTACK' ? 'attack' : 'defense';
        var typeLabel = event.eventType === 'ATTACK' ? '攻击' : '防御';
        var severityBadge = '';
        if (event.severity) {
            var severityClass = '';
            var severityText = '';
            if (event.severity === 'CRITICAL') {
                severityClass = 'danger';
                severityText = '严重';
            } else if (event.severity === 'HIGH') {
                severityClass = 'danger';
                severityText = '高危';
            } else if (event.severity === 'MEDIUM') {
                severityClass = 'warning';
                severityText = '中危';
            } else if (event.severity === 'LOW') {
                severityClass = 'info';
                severityText = '低危';
            }
            if (severityClass) {
                severityBadge = '<span class="tag ' + severityClass + '" style="margin-left: 8px; font-size: 12px;">' + severityText + '</span>';
            }
        }
        html += '<div class="timeline-item">' +
            '<div class="timeline-dot ' + dotClass + '"></div>' +
            '<div class="timeline-time">' + formatProfileDateTime(event.time) + '</div>' +
            '<div class="timeline-content">' +
            '<div class="timeline-title-text"><span class="tag ' + (dotClass === 'attack' ? 'danger' : 'success') + '" style="font-size: 12px; margin-right: 8px;">' + typeLabel + '</span>' + (event.title || '') + severityBadge + '</div>' +
            '<div class="timeline-desc">' + (event.description || '') + '</div>' +
            '</div>' +
            '</div>';
    });
    html += '</div>';
    
    container.innerHTML = html;
}

function loadAttackRecords(ip) {
    http.get('/trace/attacks/' + ip, { pageNum: attackPageNum, pageSize: attackPageSize })
        .then(function(data) {
            if (data) {
                renderAttackTable(data.list);
                renderProfilePagination(data.total, attackPageNum, attackPageSize);
            }
        })
        .catch(function(error) {
            console.error('获取攻击记录失败:', error);
        });
}

function renderAttackTable(list) {
    var tbody = document.getElementById('attackTableBody');
    
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无攻击记录</td></tr>';
        return;
    }
    
    var html = '';
    list.forEach(function(attack) {
        html += '<tr>' +
            '<td>' + formatProfileDateTime(attack.createTime) + '</td>' +
            '<td>' + getAttackTypeName(attack.attackType) + '</td>' +
            '<td>' + (attack.targetUri || '-') + '</td>' +
            '<td>' + getRiskLevelBadge(attack.riskLevel) + '</td>' +
            '<td>' + getHandledBadge(attack.handled) + '</td>' +
            '</tr>';
    });
    
    tbody.innerHTML = html;
}

function renderProfilePagination(total, pageNum, pageSize) {
    var container = document.getElementById('profilePagination');
    var totalPages = Math.ceil(total / pageSize);
    
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    
    var html = '<span class="page-info">共 ' + total + ' 条</span>';
    
    if (pageNum > 1) {
        html += '<button class="page-btn" onclick="goToProfilePage(' + (pageNum - 1) + ')">上一页</button>';
    }
    
    html += '<span class="page-current">' + pageNum + ' / ' + totalPages + '</span>';
    
    if (pageNum < totalPages) {
        html += '<button class="page-btn" onclick="goToProfilePage(' + (pageNum + 1) + ')">下一页</button>';
    }
    
    container.innerHTML = html;
}

function goToProfilePage(page) {
    attackPageNum = page;
    if (currentProfileIp) {
        loadAttackRecords(currentProfileIp);
    }
}

function formatProfileDateTime(dateStr) {
    if (!dateStr) return '-';
    var date = new Date(dateStr);
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');
    var hour = String(date.getHours()).padStart(2, '0');
    var minute = String(date.getMinutes()).padStart(2, '0');
    var second = String(date.getSeconds()).padStart(2, '0');
    return year + '-' + month + '-' + day + ' ' + hour + ':' + minute + ':' + second;
}

function getAttackTypeName(type) {
    var types = {
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
    var badges = {
        'CRITICAL': '<span class="tag danger">严重</span>',
        'HIGH': '<span class="tag danger">高危</span>',
        'MEDIUM': '<span class="tag warning">中危</span>',
        'LOW': '<span class="tag info">低危</span>'
    };
    return badges[level] || '<span class="tag">未知</span>';
}

function getHandledBadge(handled) {
    if (handled === 1) {
        return '<span class="tag success">已处理</span>';
    }
    return '<span class="tag warning">未处理</span>';
}
