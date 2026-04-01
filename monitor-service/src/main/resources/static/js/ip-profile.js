let currentIp = null;
let attackPageNum = 1;
const attackPageSize = 10;

function init() {
    const urlParams = new URLSearchParams(window.location.search);
    currentIp = urlParams.get('ip');
    
    if (!currentIp) {
        message.error('缺少IP参数');
        return;
    }
    
    loadIpProfile();
    loadAttackChain();
    loadAttackRecords();
}

function loadIpProfile() {
    http.get('/trace/ip/' + currentIp)
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
    
    let location = [];
    if (profile.geoInfo && profile.geoInfo.valid) {
        if (profile.geoInfo.country) location.push(profile.geoInfo.country);
        if (profile.geoInfo.province) location.push(profile.geoInfo.province);
        if (profile.geoInfo.city) location.push(profile.geoInfo.city);
    }
    document.getElementById('profileLocation').textContent = location.length > 0 ? location.join(' · ') : '未知位置';
    
    let statusText = profile.currentStatus || '正常';
    let statusClass = 'status-normal';
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
    document.getElementById('firstSeen').textContent = formatDateTime(profile.firstSeen);
    document.getElementById('lastSeen').textContent = formatDateTime(profile.lastSeen);
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
        document.getElementById('blacklistedAt').textContent = formatDateTime(profile.blacklistedAt);
        document.getElementById('blacklistExpire').textContent = profile.blacklistExpireTime 
            ? formatDateTime(profile.blacklistExpireTime) : '永久封禁';
        document.getElementById('blacklistReason').textContent = profile.blacklistReason || '-';
    }
    
    renderAttackTypeStats(profile.attackTypeStats);
    renderHourlyChart(profile.hourlyStats);
}

function renderRiskScore(score, level) {
    const scoreCircle = document.getElementById('riskScoreCircle');
    const scoreValue = document.getElementById('riskScoreValue');
    const levelText = document.getElementById('riskLevelText');
    const levelDesc = document.getElementById('riskLevelDesc');
    
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
    const container = document.getElementById('attackTypeList');
    
    if (!stats || stats.length === 0) {
        container.innerHTML = '<li class="no-data">暂无攻击记录</li>';
        return;
    }
    
    let html = '';
    stats.forEach(function(stat) {
        const percentage = stat.percentage || 0;
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
    const container = document.getElementById('hourlyChart');
    
    if (!hourlyStats || hourlyStats.length === 0) {
        container.innerHTML = '<div class="no-data">暂无数据</div>';
        return;
    }
    
    const maxCount = Math.max(...hourlyStats.map(s => s.count || 0), 1);
    
    let html = '';
    for (let i = 0; i < 24; i++) {
        const stat = hourlyStats.find(s => s.hour === i) || { hour: i, count: 0 };
        const height = maxCount > 0 ? (stat.count / maxCount * 140) : 0;
        html += '<div class="bar-item" style="height: ' + Math.max(height, 4) + 'px;" title="' + i + '时: ' + stat.count + '次">' +
            '<span class="bar-label">' + i + '</span>' +
            '</div>';
    }
    
    container.innerHTML = html;
}

function loadAttackChain() {
    http.get('/trace/chain/' + currentIp, { hours: 24 })
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
    const container = document.getElementById('timeline');
    
    if (!timeline || timeline.length === 0) {
        container.innerHTML = '<div class="no-data">暂无攻击事件</div>';
        return;
    }
    
    let html = '';
    timeline.slice(0, 20).forEach(function(event) {
        const dotClass = event.eventType === 'ATTACK' ? 'attack' : 'defense';
        html += '<div class="timeline-item">' +
            '<div class="timeline-dot ' + dotClass + '"></div>' +
            '<div class="timeline-time">' + formatDateTime(event.time) + '</div>' +
            '<div class="timeline-content">' +
            '<div class="timeline-title-text">' + event.title + '</div>' +
            '<div class="timeline-desc">' + event.description + '</div>' +
            '</div>' +
            '</div>';
    });
    
    container.innerHTML = html;
}

function loadAttackRecords() {
    http.get('/trace/attacks/' + currentIp, { pageNum: attackPageNum, pageSize: attackPageSize })
        .then(function(data) {
            if (data) {
                renderAttackTable(data.list);
                renderPagination(data.total, attackPageNum, attackPageSize);
            }
        })
        .catch(function(error) {
            console.error('获取攻击记录失败:', error);
        });
}

function renderAttackTable(list) {
    const tbody = document.getElementById('attackTableBody');
    
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">暂无攻击记录</td></tr>';
        return;
    }
    
    let html = '';
    list.forEach(function(attack) {
        html += '<tr>' +
            '<td>' + formatDateTime(attack.createTime) + '</td>' +
            '<td>' + getAttackTypeName(attack.attackType) + '</td>' +
            '<td>' + (attack.targetUri || '-') + '</td>' +
            '<td>' + getRiskLevelBadge(attack.riskLevel) + '</td>' +
            '<td>' + getHandledBadge(attack.handled) + '</td>' +
            '</tr>';
    });
    
    tbody.innerHTML = html;
}

function renderPagination(total, pageNum, pageSize) {
    const container = document.getElementById('pagination');
    const totalPages = Math.ceil(total / pageSize);
    
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    
    let html = '<span class="page-info">共 ' + total + ' 条</span>';
    
    if (pageNum > 1) {
        html += '<button class="page-btn" onclick="goToPage(' + (pageNum - 1) + ')">上一页</button>';
    }
    
    html += '<span class="page-current">' + pageNum + ' / ' + totalPages + '</span>';
    
    if (pageNum < totalPages) {
        html += '<button class="page-btn" onclick="goToPage(' + (pageNum + 1) + ')">下一页</button>';
    }
    
    container.innerHTML = html;
}

function goToPage(page) {
    attackPageNum = page;
    loadAttackRecords();
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
