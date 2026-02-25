/**
 * SQL注入漏洞测试平台JavaScript逻辑
 * 实现前后端交互、数据库查询、结果展示等功能
 */

// 全局配置
const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/sql/query',
        SAFE: '/target/sql/safe-query'
    },
    SELECTORS: {
        USER_ID_INPUT: '#userIdInput',
        VULN_QUERY_BTN: '#vulnQueryBtn',
        SAFE_QUERY_BTN: '#safeQueryBtn',
        OUTPUT_CONTAINER: '#outputContainer',
        STATUS_INDICATOR: '#statusIndicator',
        LOADING_OVERLAY: '#loadingOverlay',
        SLEEP_SLIDER: '#sleepTimeSlider',
        SLEEP_DISPLAY: '#sleepTimeDisplay'
    }
};

// 全局状态
const state = {
    queryHistory: [],
    currentHistoryIndex: -1
};

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    bindSliderEvents();
});

/**
 * 应用初始化函数
 */
function initializeApp() {
    bindEventListeners();
    updateStatus('ready', '就绪');
    console.log('SQL漏洞测试平台初始化完成');
}

/**
 * 绑定滑块事件
 */
function bindSliderEvents() {
    const slider = document.querySelector(CONFIG.SELECTORS.SLEEP_SLIDER);
    const display = document.querySelector(CONFIG.SELECTORS.SLEEP_DISPLAY);
    
    if (slider && display) {
        slider.addEventListener('input', () => {
            display.textContent = `${slider.value}秒`;
        });
    }
}

/**
 * 绑定事件监听器
 */
function bindEventListeners() {
    // 查询按钮事件
    document.querySelector(CONFIG.SELECTORS.VULN_QUERY_BTN)
        ?.addEventListener('click', () => executeQuery('vuln'));
    
    document.querySelector(CONFIG.SELECTORS.SAFE_QUERY_BTN)
        ?.addEventListener('click', () => executeQuery('safe'));
    
    // 回车键执行查询
    document.querySelector(CONFIG.SELECTORS.USER_ID_INPUT)
        ?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') executeQuery('vuln');
        });
    
    // 输出容器自动滚动
    const outputContainer = document.querySelector(CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (outputContainer) {
        new MutationObserver(() => {
            outputContainer.scrollTop = outputContainer.scrollHeight;
        }).observe(outputContainer, { childList: true, subtree: true });
    }
}

/**
 * 执行查询主函数
 */
function executeQuery(queryType) {
    const userIdInput = document.querySelector(CONFIG.SELECTORS.USER_ID_INPUT);
    const userId = userIdInput?.value.trim();
    
    // 输入验证
    if (!userId) {
        showNotification('请输入用户ID', 'warning');
        userIdInput?.focus();
        return;
    }
    
    // 记录时间和状态
    const startTime = performance.now();
    addToHistory(userId);
    showLoading(true);
    updateStatus('executing', '查询中...');
    
    // 确定API端点
    const apiEndpoint = queryType === 'vuln' 
        ? CONFIG.ENDPOINTS.VULN 
        : CONFIG.ENDPOINTS.SAFE;
    
    // 发送请求
    sendQueryRequest(apiEndpoint, userId, queryType)
        .then(response => handleResponse(userId, response, queryType, startTime, true))
        .catch(error => handleResponse(userId, error, queryType, startTime, false))
        .finally(() => {
            showLoading(false);
            updateStatus('ready', '就绪');
            if (userIdInput) userIdInput.value = '';
        });
}

/**
 * 统一处理响应
 */
function handleResponse(userId, data, queryType, startTime, isSuccess) {
    const responseTime = ((performance.now() - startTime) / 1000).toFixed(2);
    
    if (isSuccess) {
        handleQueryResponse(userId, data, queryType, responseTime);
    } else {
        handleQueryError(userId, data, queryType, responseTime);
    }
}

/**
 * 发送查询请求到后端API
 */
async function sendQueryRequest(endpoint, userId, queryType) {
    const url = `${endpoint}?id=${encodeURIComponent(userId)}`;
    
    try {
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        // 尝试解析JSON响应
        const responseText = await response.text();
        try {
            return JSON.parse(responseText);
        } catch (jsonError) {
            // 如果JSON解析失败，返回原始文本
            console.warn('JSON解析失败，返回原始响应:', responseText);
            return {
                code: response.status,
                msg: '响应解析失败',
                data: {},
                rawResponse: responseText
            };
        }
    } catch (error) {
        throw error;
    }
}

/**
 * 处理查询响应
 */
function handleQueryResponse(userId, response, queryType, responseTime) {
    console.log('查询响应:', response);
    
    // 解析响应数据
    const resultData = {
        userId: userId,
        queryType: queryType,
        timestamp: new Date().toLocaleString('zh-CN'),
        success: response.code === 200 || !response.code,
        message: response.msg || response.message || '查询完成',
        data: response.data || {},
        rawResponse: response,
        responseTime: responseTime // 添加响应时间
    };
    
    // 特殊处理500错误
    if (response.code === 500 && response.data) {
        resultData.isError = true;
        resultData.errorMessage = response.data.error_message || response.error || 'SQL执行失败';
        resultData.errorType = response.data.error_type || 'UNKNOWN';
    }
    
    // 显示结果
    displayQueryResult(resultData);
    
    // 显示通知
    if (resultData.success) {
        showNotification(`${queryType === 'vuln' ? '漏洞' : '安全'}查询成功: ${userId}`, 'success');
    } else {
        showNotification(`查询失败: ${resultData.message}`, 'error');
    }
}

/**
 * 处理查询错误
 */
function handleQueryError(userId, error, queryType, responseTime) {
    console.error('查询错误:', error);
    
    const errorData = {
        userId: userId,
        queryType: queryType,
        timestamp: new Date().toLocaleString('zh-CN'),
        success: false,
        message: error.message || '网络请求失败',
        error: error,
        isError: true,
        responseTime: responseTime // 添加响应时间
    };
    
    displayQueryResult(errorData);
    showNotification(`查询出错: ${error.message}`, 'error');
}

/**
 * 显示查询结果
 */
function displayQueryResult(resultData) {
    const outputContainer = document.querySelector(CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;
    
    // 移除欢迎消息
    outputContainer.querySelector('.welcome-message')?.remove();
    
    // 创建并添加结果元素
    const resultElement = createElement('div', 'query-result', 
        buildResultHTML(resultData)
    );
    
    outputContainer.appendChild(resultElement);
    outputContainer.scrollTop = outputContainer.scrollHeight;
}

/**
 * 构建结果HTML
 */
function buildResultHTML(resultData) {
    return `
        <div class="query-header">
            <div>
                <span class="query-type ${resultData.queryType}">${getTypeLabel(resultData.queryType)}</span>
                <span class="ms-2">用户ID: ${escapeHtml(resultData.userId)}</span>
                ${resultData.responseTime ? `<span class="ms-3 badge bg-info">耗时: ${resultData.responseTime}秒</span>` : ''}
            </div>
            <div class="query-timestamp">${resultData.timestamp}</div>
        </div>
        <div class="query-input">
            <i class="fas fa-user me-2"></i>查询条件: id = ${escapeHtml(resultData.userId)}
        </div>
        ${formatQueryDetails(resultData)}
    `;
}

/**
 * 创建DOM元素工具函数
 */
function createElement(tag, className, innerHTML = '') {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (innerHTML) element.innerHTML = innerHTML;
    return element;
}

/**
 * 获取查询类型标签
 */
function getTypeLabel(queryType) {
    return queryType === 'vuln' ? '漏洞查询' : '安全查询';
}

/**
 * 格式化查询详情显示 - 支持多语句结果
 */
function formatQueryDetails(resultData) {
    if (resultData.isError) {
        let errorHtml = `
        <div class="query-error">
            <i class="fas fa-exclamation-circle me-2"></i>
            <strong>错误类型:</strong> ${escapeHtml(resultData.errorType || '未知')}<br>
            <strong>错误信息:</strong> ${escapeHtml(resultData.errorMessage || resultData.message)}<br>
        `;
        
        // 针对特定错误类型提供修复建议
        if (resultData.errorType === 'UNION_COLUMN_MISMATCH') {
            errorHtml += `<br><strong>修复建议:</strong> UNION查询需要两侧SELECT语句返回相同数量的列<br>
            示例: 1 UNION SELECT 1,database(),version(),@@datadir,null`;
        }
        
        // 显示技术详情
        if (resultData.error?.stack) {
            errorHtml += `<br><strong>技术详情:</strong> ${escapeHtml(resultData.error.stack)}`;
        }
        
        errorHtml += `
        </div>
        `;
        return errorHtml;
    }
    
    // 处理成功的响应
    let detailsHtml = '';
    
    // 如果有原始响应文本，优先显示
    if (resultData.rawResponse && typeof resultData.rawResponse === 'string') {
        detailsHtml += `
        <div class="query-sql">
            <i class="fas fa-code me-2"></i><strong>原始响应:</strong><br>
            <pre style="white-space: pre-wrap; word-break: break-word;">${escapeHtml(resultData.rawResponse)}</pre>
        </div>
        `;
        return detailsHtml;
    }
    
    if (resultData.data && typeof resultData.data === 'object') {
        // 显示执行的SQL语句
        if (resultData.data.executed_sql) {
            detailsHtml += `
            <div class="query-sql">
                <i class="fas fa-code me-2"></i><strong>执行SQL:</strong><br>
                ${highlightSQL(escapeHtml(resultData.data.executed_sql))}
            </div>
            `;
        }
        
        // 处理多语句结果（堆叠查询）
        if (resultData.data.statement_results && Array.isArray(resultData.data.statement_results)) {
            resultData.data.statement_results.forEach((stmtResult, index) => {
                detailsHtml += `
                <div class="query-data">
                    <h6><i class="fas fa-code me-2"></i>查询结果（第${stmtResult.statement_index}条SQL，${stmtResult.row_count} 条记录）:</h6>
                    ${generateDynamicTable(stmtResult.rows)}
                </div>
                `;
            });
        }
        // 处理传统的单语句结果
        else if (resultData.data.user_list && Array.isArray(resultData.data.user_list)) {
            if (resultData.data.user_list.length > 0) {
                detailsHtml += `
                <div class="query-data">
                    <h6><i class="fas fa-table me-2"></i>查询结果 (${resultData.data.user_list.length} 条记录):</h6>
                    ${generateUserTable(resultData.data.user_list)}
                </div>
                `;
            } else {
                // 特殊处理时间盲注：即使没有记录也要显示响应时间和其他信息
                if (resultData.userId.includes('SLEEP')) {
                    // 先执行普通查询获取用户信息
                    fetchUserInfoForTimeBasedBlind(resultData);
                    detailsHtml += `
                    <div class="alert alert-warning">
                        <i class="fas fa-clock me-2"></i>
                        <strong>时间盲注检测:</strong><br>
                        执行耗时: <span class="badge bg-info">${resultData.responseTime}秒</span><br>
                        结果: 未找到匹配记录（这是正常的，因为SLEEP函数会延迟执行）<br>
                        <small class="text-muted">正在获取用户信息...</small>
                    </div>
                    `;
                } else {
                    detailsHtml += `
                    <div class="alert alert-info">
                        <i class="fas fa-info-circle me-2"></i>查询完成，但未找到匹配的用户记录
                    </div>
                    `;
                }
            }
        }
        
        // 显示警告信息
        if (resultData.data.warning) {
            detailsHtml += `
            <div class="query-warning">
                <i class="fas fa-exclamation-triangle me-2"></i>
                <strong>安全警告:</strong> ${escapeHtml(resultData.data.warning)}
            </div>
            `;
        }
    } else {
        // 原始响应数据
        detailsHtml = `
        <div class="query-sql">
            <strong>原始响应:</strong><br>
            <pre>${escapeHtml(JSON.stringify(resultData.rawResponse, null, 2))}</pre>
        </div>
        `;
    }
    
    return detailsHtml;
}

/**
 * 为时间盲注获取用户信息
 */
function fetchUserInfoForTimeBasedBlind(resultData) {
    // 执行一个额外的查询来获取用户信息
    const normalUserId = resultData.userId.split(' ')[0]; // 获取AND之前的部分
    if (normalUserId && normalUserId.match(/^\d+$/)) {
        fetch(`/target/sql/query?id=${normalUserId}`)
            .then(response => response.json())
            .then(data => {
                if (data.data && data.data.user_list && data.data.user_list.length > 0) {
                    updateUserDisplayForTimeBlind(resultData, data.data.user_list);
                }
            })
            .catch(error => {
                console.log('获取用户信息失败:', error);
            });
    }
}

/**
 * 更新时间盲注的用户信息显示
 */
function updateUserDisplayForTimeBlind(originalResultData, userList) {
    // 找到对应的结果元素并更新
    const outputContainer = document.getElementById('outputContainer');
    const resultElements = outputContainer.querySelectorAll('.query-result');
    const lastElement = resultElements[resultElements.length - 1];
    
    if (lastElement) {
        const userInfoHtml = `
        <div class="mt-3">
            <h6><i class="fas fa-user me-2"></i>用户信息:</h6>
            ${generateUserTable(userList)}
        </div>
        `;
        lastElement.insertAdjacentHTML('beforeend', userInfoHtml);
    }
}

/**
 * 表格生成器
 */
const TableGenerator = {
    // 通用值显示处理器
    displayValue(value) {
        return (value === null || value === undefined || value === '')
            ? '<span class="text-muted">-</span>'
            : escapeHtml(value);
    },
    
    // 生成用户表格
    generateUserTable(userList) {
        if (!userList || userList.length === 0) return '';
        
        const columns = [
            { key: 'id', label: 'ID' },
            { key: 'username', label: '用户名' },
            { key: 'password', label: '密码' },
            { key: 'phone', label: '电话' },
            { key: 'create_time', label: '创建时间' }
        ];
        
        return this.buildTable(columns, userList, (user, col) => 
            this.displayValue(user[col.key])
        );
    },
    
    // 生成动态表格
    generateDynamicTable(rows) {
        if (!rows || rows.length === 0) {
            return '<div class="alert alert-info">无数据返回</div>';
        }
        
        const columnNames = Object.keys(rows[0]);
        const columns = columnNames.map(name => ({ key: name, label: name }));
        
        return this.buildTable(columns, rows, (row, col) => 
            this.displayValue(row[col.key])
        );
    },
    
    // 构建表格基础结构
    buildTable(columns, data, cellRenderer) {
        const headerHtml = columns.map(col => 
            `<th>${escapeHtml(col.label)}</th>`
        ).join('');
        
        const bodyHtml = data.map(row => 
            `<tr>${columns.map(col => 
                `<td>${cellRenderer(row, col)}</td>`
            ).join('')}</tr>`
        ).join('');
        
        return `
        <table class="user-table">
            <thead><tr>${headerHtml}</tr></thead>
            <tbody>${bodyHtml}</tbody>
        </table>
        `;
    }
};

// 便捷函数
function generateUserTable(userList) {
    return TableGenerator.generateUserTable(userList);
}

function generateDynamicTable(rows) {
    return TableGenerator.generateDynamicTable(rows);
}

/**
 * SQL语法高亮（简单实现）
 */
function highlightSQL(sql) {
    if (!sql) return '';
    
    // 关键字高亮
    const keywords = ['SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'UNION', 'DROP', 'DELETE', 'UPDATE', 'INSERT'];
    let highlighted = sql;
    
    keywords.forEach(keyword => {
        const regex = new RegExp(`\\b${keyword}\\b`, 'gi');
        highlighted = highlighted.replace(regex, `<span class="sql-keyword">${keyword}</span>`);
    });
    
    // 字符串高亮
    highlighted = highlighted.replace(/'[^']*'/g, '<span class="sql-string">$&</span>');
    
    // 注释高亮
    highlighted = highlighted.replace(/--.*$/gm, '<span class="sql-comment">$&</span>');
    
    return highlighted;
}

/**
 * HTML转义函数（防止XSS）
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 执行测试用例
 */
function executeTestCase(testCase) {
    const userIdInput = document.getElementById('userIdInput');
    userIdInput.value = testCase;
    userIdInput.focus();
    setTimeout(() => executeQuery('vuln'), 100);
}

/**
 * 清空输出
 */
function clearOutput() {
    const outputContainer = document.getElementById('outputContainer');
    outputContainer.innerHTML = `
        <div class="welcome-message p-4 text-center text-muted">
            <i class="fas fa-database fa-3x mb-3"></i>
            <h4>查询结果已清空</h4>
            <p class="mb-0">请重新输入用户ID开始测试</p>
        </div>
    `;
    showNotification('查询结果已清空', 'info');
}

/**
 * 历史记录管理
 */
const HistoryManager = {
    MAX_SIZE: 50,
    
    add(userId) {
        // 避免重复添加
        if (state.queryHistory.length === 0 || 
            state.queryHistory[state.queryHistory.length - 1] !== userId) {
            state.queryHistory.push(userId);
            // 限制历史记录数量
            if (state.queryHistory.length > this.MAX_SIZE) {
                state.queryHistory.shift();
            }
        }
        state.currentHistoryIndex = state.queryHistory.length;
    }
};

function addToHistory(userId) {
    HistoryManager.add(userId);
}

/**
 * 状态管理
 */
const StatusManager = {
    update(status, text) {
        const indicator = document.querySelector(CONFIG.SELECTORS.STATUS_INDICATOR);
        if (indicator) {
            indicator.className = `badge ${this.getClass(status)}`;
            indicator.innerHTML = `${this.getIcon(status)} ${text}`;
        }
    },
    
    getClass(status) {
        const classes = {
            'executing': 'bg-warning status-executing',
            'success': 'bg-success status-success',
            'error': 'bg-danger status-error'
        };
        return classes[status] || 'bg-secondary';
    },
    
    getIcon(status) {
        const icons = {
            'executing': '<i class="fas fa-circle-notch fa-spin"></i>',
            'success': '<i class="fas fa-check"></i>',
            'error': '<i class="fas fa-times"></i>'
        };
        return icons[status] || '<i class="fas fa-circle"></i>';
    }
};

/**
 * 加载状态管理
 */
const LoadingManager = {
    show(show) {
        const overlay = document.querySelector(CONFIG.SELECTORS.LOADING_OVERLAY);
        if (overlay) {
            overlay.classList.toggle('show', show);
        }
    }
};

// 便捷函数
function updateStatus(status, text) {
    StatusManager.update(status, text);
}

function showLoading(show) {
    LoadingManager.show(show);
}

/**
 * 通知系统
 */
const NotificationManager = {
    DEFAULT_DURATION: 3000,
    
    show(message, type = 'info') {
        const alertDiv = this.createElement(message, type);
        this.setStyle(alertDiv);
        document.body.appendChild(alertDiv);
        
        setTimeout(() => {
            if (alertDiv.parentNode) {
                alertDiv.remove();
            }
        }, this.DEFAULT_DURATION);
    },
    
    createElement(message, type) {
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${this.getTypeClass(type)} alert-banner fade show`;
        alertDiv.role = 'alert';
        alertDiv.innerHTML = `
            <i class="${this.getIconClass(type)} me-2"></i>
            ${escapeHtml(message)}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        return alertDiv;
    },
    
    setStyle(element) {
        Object.assign(element.style, {
            position: 'fixed',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            zIndex: '10000',
            minWidth: '300px',
            textAlign: 'center'
        });
    },
    
    getTypeClass(type) {
        const classes = {
            'success': 'success',
            'error': 'danger',
            'warning': 'warning'
        };
        return classes[type] || 'info';
    },
    
    getIconClass(type) {
        const icons = {
            'success': 'fas fa-check-circle',
            'error': 'fas fa-exclamation-circle',
            'warning': 'fas fa-exclamation-triangle'
        };
        return icons[type] || 'fas fa-info-circle';
    }
};

function showNotification(message, type = 'info') {
    NotificationManager.show(message, type);
}

/**
 * 导出测试报告
 */
function exportTestReport() {
    const outputContainer = document.getElementById('outputContainer');
    const reportContent = `
SQL注入漏洞测试报告
========================

生成时间: ${new Date().toLocaleString('zh-CN')}
测试环境: SQL注入漏洞测试平台

测试记录:
${outputContainer.innerText}

---
报告生成完毕
    `.trim();
    
    // 创建下载链接
    const blob = new Blob([reportContent], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sql-vuln-test-report-${new Date().getTime()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    showNotification('测试报告已导出', 'success');
}

// 执行时间盲注（使用动态时间参数）
function executeSleepInjection() {
    const sleepTime = document.getElementById('sleepTimeSlider').value;
    const payload = `1 AND SLEEP(${sleepTime})`;
    const userIdInput = document.getElementById('userIdInput');
    userIdInput.value = payload;
    executeQuery('vuln');
}



// 暴露全局函数供HTML调用
Object.assign(window, {
    executeTestCase,
    executeSleepInjection,
    clearOutput,
    exportTestReport
});