/**
 * @typedef {Object} TargetInfo
 * @property {string} method
 * @property {string} path
 * @property {string} desc
 * @property {string} [vulnerability]
 */

/**
 * @typedef {Object} ServerResponseData
 * @property {number} [request_id]
 * @property {number} [cost_time_ms]
 * @property {number} [server_processing_time_ms]
 * @property {number} [server_response_time_ms]
 * @property {number} [actual_cost_time_ms]
 * @property {number} [simulated_delay_ms]
 * @property {number} [total_requests]
 * @property {number} [total_requests_received]
 * @property {number} [status_check_timestamp]
 * @property {string} [warning]
 * @property {string} [security_warning]
 * @property {string} [tip]
 * @property {TargetInfo[]} [available_targets]
 */

/**
 * @typedef {Object} ApiResponse
 * @property {number} code
 * @property {string} msg
 * @property {ServerResponseData} [data]
 */

/**
 * DDoS攻击模拟靶场JavaScript逻辑
 * 实现前后端交互、攻击模拟、结果展示等功能
 */

// 全局配置
const DDOS_CONFIG = {
    ENDPOINTS: {
        COMPUTE_HEAVY: '/target/ddos/compute-heavy',
        IO_DELAY: '/target/ddos/io-delay',
        PING: '/target/ddos/ping',
        STATUS: '/target/ddos/status'
    },
    SELECTORS: {
        CONCURRENT_REQUESTS: '#concurrentRequests',
        ATTACK_DURATION: '#attackDuration',
        REQUEST_INTERVAL: '#requestInterval',
        BATCH_REQUEST_COUNT: '#batchRequestCount',
        HIGH_FREQ_REQUEST_COUNT: '#highFreqRequestCount',
        MAX_CONCURRENT_LIMIT: '#maxConcurrentLimit',
        CONCURRENT_DISPLAY: '#concurrentDisplay',
        DURATION_DISPLAY: '#durationDisplay',
        INTERVAL_DISPLAY: '#intervalDisplay',
        BATCH_COUNT_DISPLAY: '#batchCountDisplay',
        HIGH_FREQ_COUNT_DISPLAY: '#highFreqCountDisplay',
        MAX_CONCURRENT_DISPLAY: '#maxConcurrentDisplay',
        CUSTOM_ENDPOINT: '#customEndpoint',
        CUSTOM_ATTACK_BTN: '#customAttackBtn',
        ATTACK_LOG_CONTAINER: '#attackLogContainer',
        STATUS_INDICATOR: '#statusIndicator',
        LOADING_OVERLAY: '#loadingOverlay',
        ATTACK_PROGRESS: '#attackProgress',
        PROGRESS_TEXT: '#progressText'
    }
};

// 全局状态（修改为可代理对象）
const ddosState = {
    attackInProgress: false,
    attackLogs: [],
    attackStats: {
        totalRequests: 0,
        successfulRequests: 0,
        failedRequests: 0,
        startTime: null,
        endTime: null
    }
};

// 添加同步锁机制
const attackLock = {
    isCompletedLogged: false,
    completionPromise: null
};

// 新增原子递增方法（使用临时变量避免竞态条件）
ddosState.increment = (key, delta) => {
    // 使用临时变量确保原子操作
    const currentValue = ddosState.attackStats[key];
    ddosState.attackStats[key] = currentValue + delta;
};

// 重置攻击状态函数
ddosState.resetAttackState = () => {
    ddosState.attackStats.totalRequests = 0;
    ddosState.attackStats.successfulRequests = 0;
    ddosState.attackStats.failedRequests = 0;
    ddosState.attackStats.startTime = null;
    ddosState.attackStats.endTime = null;
    attackLock.isCompletedLogged = false;
    attackLock.completionPromise = null;
};


// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeDdosApp();
    bindSliderEvents();
    bindCustomAttackButton();
});

/**
 * 应用初始化函数
 */
function initializeDdosApp() {
    bindEventListeners();
    updateStatus('ready', '待命中');
    console.log('DDoS攻击模拟靶场初始化完成');
}

/**
 * 绑定滑块事件
 */
function bindSliderEvents() {
    // 并发请求数滑块
    const concurrentSlider = document.querySelector(DDOS_CONFIG.SELECTORS.CONCURRENT_REQUESTS);
    const concurrentDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.CONCURRENT_DISPLAY);

    if (concurrentSlider && concurrentDisplay) {
        concurrentSlider.addEventListener('input', () => {
            concurrentDisplay.textContent = concurrentSlider.value;
        });
    }

    // 攻击持续时间滑块
    const durationSlider = document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_DURATION);
    const durationDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.DURATION_DISPLAY);

    if (durationSlider && durationDisplay) {
        durationSlider.addEventListener('input', () => {
            durationDisplay.textContent = `${durationSlider.value}s`;
        });
    }

    // 请求间隔滑块
    const intervalSlider = document.querySelector(DDOS_CONFIG.SELECTORS.REQUEST_INTERVAL);
    const intervalDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.INTERVAL_DISPLAY);

    if (intervalSlider && intervalDisplay) {
        intervalSlider.addEventListener('input', () => {
            intervalDisplay.textContent = `${intervalSlider.value}ms`;
        });
    }

    // 批量攻击请求数滑块
    const batchCountSlider = document.querySelector(DDOS_CONFIG.SELECTORS.BATCH_REQUEST_COUNT);
    const batchCountDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.BATCH_COUNT_DISPLAY);

    if (batchCountSlider && batchCountDisplay) {
        batchCountSlider.addEventListener('input', () => {
            batchCountDisplay.textContent = batchCountSlider.value;
        });
    }

    // 高频攻击请求数滑块
    const highFreqCountSlider = document.querySelector(DDOS_CONFIG.SELECTORS.HIGH_FREQ_REQUEST_COUNT);
    const highFreqCountDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.HIGH_FREQ_COUNT_DISPLAY);

    if (highFreqCountSlider && highFreqCountDisplay) {
        highFreqCountSlider.addEventListener('input', () => {
            highFreqCountDisplay.textContent = highFreqCountSlider.value;
        });
    }

    // 最大并发请求数滑块
    const maxConcurrentSlider = document.querySelector(DDOS_CONFIG.SELECTORS.MAX_CONCURRENT_LIMIT);
    const maxConcurrentDisplay = document.querySelector(DDOS_CONFIG.SELECTORS.MAX_CONCURRENT_DISPLAY);

    if (maxConcurrentSlider && maxConcurrentDisplay) {
        maxConcurrentSlider.addEventListener('input', () => {
            maxConcurrentDisplay.textContent = maxConcurrentSlider.value;
        });
    }
}

/**
 * 绑定事件监听器
 */
function bindEventListeners() {
    // 输出容器自动滚动
    const logContainer = document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_LOG_CONTAINER);
    if (logContainer) {
        new MutationObserver(() => {
            logContainer.scrollTop = logContainer.scrollHeight;
        }).observe(logContainer, { childList: true, subtree: true });
    }
}

/**
 * 绑定自定义攻击按钮
 */
function bindCustomAttackButton() {
    const customBtn = document.querySelector(DDOS_CONFIG.SELECTORS.CUSTOM_ATTACK_BTN);
    if (customBtn) {
        customBtn.addEventListener('click', async () => {
            const endpoint = document.querySelector(DDOS_CONFIG.SELECTORS.CUSTOM_ENDPOINT).value.trim();
            if (endpoint) {
                try {
                    await executeDdosAttack(endpoint, '自定义攻击');
                } catch (error) {
                    console.error('自定义攻击执行失败:', error);
                }
            } else {
                showNotification('请输入攻击端点', 'warning');
            }
        });
    }
}

/**
 * 执行DDoS攻击主函数
 */
async function executeDdosAttack(endpoint, attackType) {
    if (ddosState.attackInProgress) {
        showNotification('已有攻击正在进行中，请稍后再试', 'warning');
        return;
    }

    // 获取攻击参数
    const concurrentRequests = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.CONCURRENT_REQUESTS)?.value || '10');
    const attackDuration = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_DURATION)?.value || '30');
    const requestInterval = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.REQUEST_INTERVAL)?.value || '100');

    // 重置攻击状态
    ddosState.resetAttackState();

    // 记录攻击开始
    ddosState.attackInProgress = true;
    ddosState.attackStats.startTime = new Date();

    // 更新UI状态
    showLoading(true);
    updateStatus('executing', '攻击进行中...');
    updateProgressBar(0, '攻击启动中...');

    // 记录攻击日志
    logAttackStart(attackType, endpoint, concurrentRequests, attackDuration);

    try {
        // 执行攻击
        await performAttack(endpoint, concurrentRequests, attackDuration, requestInterval);

        // 攻击完成
        ddosState.attackStats.endTime = new Date();
        logAttackCompletion(attackType, endpoint);
        showNotification(`${attackType}执行完成`, 'success');

    } catch (error) {
        console.error('攻击执行出错:', error);
        logAttackError(attackType, endpoint, error);
        showNotification(`攻击执行失败: ${error.message}`, 'error');
    } finally {
        // 重置状态
        ddosState.attackInProgress = false;
        showLoading(false);
        updateStatus('ready', '待命中');
    }
}

/**
 * 执行具体的攻击逻辑
 */
async function performAttack(endpoint, concurrentRequests, durationSeconds, intervalMs) {
    const startTime = Date.now();
    const durationMs = durationSeconds * 1000;
    // 创建并发请求Promise数组
    const promises = [];

    for (let i = 0; i < concurrentRequests; i++) {
        promises.push(sendConcurrentRequests(endpoint, startTime, durationMs, intervalMs, i));
    }

    // 等待所有并发请求完成
    await Promise.all(promises);

    // 更新进度到最后
    updateProgressBar(100, '攻击已完成');
}

/**
 * 发送并发请求
 * @param {string} endpoint
 * @param {number} startTime
 * @param {number} durationMs
 * @param {number} intervalMs
 * @param {number} threadId
 */
async function sendConcurrentRequests(endpoint, startTime, durationMs, intervalMs, threadId) {
    let requestCount = 0;
    while (Date.now() - startTime < durationMs) {
        try {
            const response = await fetch(endpoint, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                credentials: 'same-origin'
            });

            ddosState.increment('totalRequests', 1);
            requestCount++;

            if (response.ok) {
                ddosState.increment('successfulRequests', 1);
                /** @type {ApiResponse} */
                const data = await response.json();
                logSuccessfulRequest(threadId, endpoint, data);
            } else {
                ddosState.increment('failedRequests', 1);
                logFailedRequest(threadId, endpoint, String(response.status));
            }

        } catch (error) {
            ddosState.increment('failedRequests', 1);
            ddosState.increment('totalRequests', 1);
            logFailedRequest(threadId, endpoint, error instanceof Error ? error.message : String(error));
        }

        // 更新进度
        const elapsed = Date.now() - startTime;
        const progress = Math.min((elapsed / durationMs) * 100, 100);
        updateProgressBar(progress, `线程${threadId + 1}活跃中...`);

        // 等待间隔时间
        await new Promise(resolve => setTimeout(resolve, intervalMs));
    }
}

/**
 * 执行批量攻击（返回 Promise）
 * @param {string} endpoint
 * @param {number} requestCount
 * @param {string} attackName
 * @returns {Promise<void>}
 */
function executeBatchAttack(endpoint, requestCount, attackName) {
    return new Promise((resolve) => {
        if (ddosState.attackInProgress) {
            showNotification('已有攻击正在进行中', 'warning');
            resolve(); // 提前结束
            return;
        }

        // 重置攻击状态
        ddosState.resetAttackState();

        // 记录攻击开始时间
        ddosState.attackStats.startTime = new Date();

        showLoading(true);
        updateStatus('executing', '批量攻击中...');
        logAttackStart(attackName, endpoint, requestCount, '批量');

        let completed = 0;
        let hasLoggedCompletion = false; // 本地标志防止重复日志

        // 获取最大并发请求数配置
        const MAX_CONCURRENT = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.MAX_CONCURRENT_LIMIT)?.value || '50');
        let activeRequests = 0;

        // 使用队列确保请求按顺序处理，避免竞态条件
        const requestQueue = [];

        function processQueue() {
            // 处理已完成的请求
            while (requestQueue.length > 0 && requestQueue[0].completed) {
                const finishedRequest = requestQueue.shift();
                completed++;

                // 更新统计信息（使用原子操作）
                ddosState.increment('totalRequests', 1);
                if (finishedRequest.success) {
                    ddosState.increment('successfulRequests', 1);
                } else {
                    ddosState.increment('failedRequests', 1);
                }

                // 记录日志
                if (finishedRequest.success) {
                    logSuccessfulRequest(finishedRequest.index, endpoint, finishedRequest.data);
                } else {
                    logFailedRequest(finishedRequest.index, endpoint, finishedRequest.error);
                }

                // 更新进度条
                const progress = (completed / requestCount) * 100;
                updateProgressBar(progress, `已完成 ${completed}/${requestCount} 请求`);
            }

            // 检查是否所有请求都已完成
            if (completed >= requestCount && requestQueue.length === 0 && !hasLoggedCompletion) {
                hasLoggedCompletion = true;
                ddosState.attackStats.endTime = new Date();
                showLoading(false);
                updateStatus('ready', '待命中');
                logAttackCompletion(attackName, endpoint);
                showNotification(`${attackName}完成`, 'success');
                resolve();
            }
        }

        function sendNextRequest() {
            // 更严格的条件检查：已完成请求数达到设定值时不再发送新请求
            if (completed >= requestCount) {
                return;
            }

            // 并发控制检查
            if (activeRequests >= MAX_CONCURRENT) {
                return;
            }

            activeRequests++;
            const currentIndex = completed + requestQueue.length;

            // 再次检查，确保不会超出请求总数
            if (currentIndex >= requestCount) {
                activeRequests--;
                return;
            }

            // 创建请求对象
            const requestObj = {
                index: currentIndex,
                completed: false,
                success: false,
                data: null,
                error: null
            };

            requestQueue.push(requestObj);

            fetch(endpoint)
                .then(response => {
                    if (response.ok) {
                        requestObj.success = true;
                        return response.json();
                    } else {
                        requestObj.success = false;
                        requestObj.error = `HTTP ${response.status}`;
                        throw new Error(requestObj.error);
                    }
                })
                .then(data => {
                    requestObj.data = data;
                })
                .catch(error => {
                    if (!requestObj.error) {
                        requestObj.error = error instanceof Error ? error.message : String(error);
                    }
                    requestObj.success = false;
                })
                .finally(() => {
                    requestObj.completed = true;
                    activeRequests--;
                    processQueue();
                    // 只有在还有剩余请求数时才继续发送
                    if (completed < requestCount) {
                        sendNextRequest();
                    }
                });
        }

        // 启动初始请求
        for (let i = 0; i < Math.min(MAX_CONCURRENT, requestCount); i++) {
            sendNextRequest();
        }
    });
}


/**
 * 执行高频攻击
 * @param {string} endpoint
 * @param {number} qps
 * @param {string} attackName
 */
async function executeHighFrequencyAttack(endpoint, qps, attackName) {
    // 获取高频攻击的请求数量配置
    const requestCount = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.HIGH_FREQ_REQUEST_COUNT)?.value || '1000');

    try {
        await executeBatchAttack(endpoint, requestCount, `${attackName} (${qps}QPS, ${requestCount}请求)`);
    } catch (error) {
        console.error('高频攻击执行失败:', error);
        showNotification(`高频攻击执行失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}



/**
 * 执行压力测试
 * @param {string} testType
 * @param {number} concurrent
 * @param {number} duration
 * @param {string} testName
 */
async function executeStressTest(testType, concurrent, duration, testName) {
    const endpoint = testType === 'cpu' ? DDOS_CONFIG.ENDPOINTS.COMPUTE_HEAVY : DDOS_CONFIG.ENDPOINTS.IO_DELAY;
    try {
        await executeDdosAttack(endpoint, testName);
    } catch (error) {
        console.error('压力测试执行失败:', error);
        showNotification(`压力测试执行失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}

/**
 * 检查攻击状态
 */
async function checkAttackStatus() {
    try {
        const response = await fetch(DDOS_CONFIG.ENDPOINTS.STATUS);
        /** @type {ApiResponse} */
        const data = await response.json();
        logStatusCheck(data);
        showNotification('状态检查完成', 'success');
    } catch (error) {
        console.error('状态检查失败:', error);
        showNotification(`状态检查失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}

/**
 * 获取系统指标
 */
async function getSystemMetrics() {
    try {
        // 这里可以扩展为获取更多系统指标
        const response = await fetch(DDOS_CONFIG.ENDPOINTS.STATUS);
        /** @type {ApiResponse} */
        const data = await response.json();

        const metrics = {
            totalRequests: data?.data?.total_requests_received || 0,
            availableTargets: Array.isArray(data?.data?.available_targets)
                ? data.data.available_targets.length
                : 0,
            timestamp: new Date().toLocaleString('zh-CN')
        };

        logSystemMetrics(metrics);
        showNotification('系统指标获取完成', 'success');
    } catch (error) {
        console.error('获取系统指标失败:', error);
        showNotification(`获取指标失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}

/**
 * 日志记录函数
 * @param {string} attackType
 * @param {string} endpoint
 * @param {number} concurrent
 * @param {number | string} duration
 */
function logAttackStart(attackType, endpoint, concurrent, duration) {
    const logEntry = {
        type: 'start',
        attackType: attackType,
        endpoint: endpoint,
        concurrent: concurrent,
        duration: duration,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * @param {string} attackType
 * @param {string} endpoint
 */
function logAttackCompletion(attackType, endpoint) {
    const logEntry = {
        type: 'completion',
        attackType: attackType,
        endpoint: endpoint,
        stats: { ...ddosState.attackStats },
        duration: ddosState.attackStats.endTime ? ddosState.attackStats.endTime - (ddosState.attackStats.startTime || 0) : 0,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * @param {string} attackType
 * @param {string} endpoint
 * @param {Error} error
 */
function logAttackError(attackType, endpoint, error) {
    const logEntry = {
        type: 'error',
        attackType: attackType,
        endpoint: endpoint,
        error: error.message,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * @param {number} threadId
 * @param {string} endpoint
 * @param {ApiResponse} responseData
 */
function logSuccessfulRequest(threadId, endpoint, responseData) {
    // 计算响应时间
    const responseTimeInfo = calculateResponseTime(responseData);

    const logEntry = {
        type: 'success',
        threadId: threadId,
        endpoint: endpoint,
        response: responseData,
        responseTimeInfo: responseTimeInfo,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * @param {number} threadId
 * @param {string} endpoint
 * @param {string} error
 */
function logFailedRequest(threadId, endpoint, error) {
    const logEntry = {
        type: 'failure',
        threadId: threadId,
        endpoint: endpoint,
        error: error,
        timestamp: new Date().toLocaleString('zh-CN'),
        responseTimeInfo: { displayTime: 'N/A', type: 'error' }
    };

    displayAttackLog(logEntry);
}

/**
 * @param {ApiResponse} statusData
 */
function logStatusCheck(statusData) {
    const logEntry = {
        type: 'status',
        data: statusData,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * @param {{totalRequests: number, availableTargets: number, timestamp: string}} metrics
 */
function logSystemMetrics(metrics) {
    const logEntry = {
        type: 'metrics',
        metrics: metrics,
        timestamp: new Date().toLocaleString('zh-CN')
    };

    displayAttackLog(logEntry);
}

/**
 * 显示攻击日志
 * @param {any} logEntry
 */
function displayAttackLog(logEntry) {
    const container = document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_LOG_CONTAINER);
    if (!container) return;

    // 移除欢迎消息
    const welcomeMessage = container.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }

    // 创建日志元素
    const logElement = document.createElement('div');
    logElement.className = 'attack-log-entry';
    logElement.innerHTML = buildLogHTML(logEntry);

    container.appendChild(logElement);
    container.scrollTop = container.scrollHeight;

    // 保存到状态中
    ddosState.attackLogs.push(logEntry);
}

/**
 * 构建日志HTML
 * @param {any} logEntry
 * @returns {string}
 */
function buildLogHTML(logEntry) {
    switch (logEntry.type) {
        case 'start':
            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type flood">攻击开始</span>
                        <span class="ms-2">${escapeHtml(logEntry.attackType)}</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-bullseye me-2"></i>攻击目标: ${escapeHtml(logEntry.endpoint)}
                </div>
                <div class="attack-details">
                    <div class="attack-metrics">
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.concurrent}</div>
                            <div class="metric-label">并发请求数</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.duration}</div>
                            <div class="metric-label">持续时间(秒)</div>
                        </div>
                    </div>
                </div>
                <div class="attack-status info">
                    <i class="fas fa-play-circle me-2"></i>
                    <strong>攻击已启动</strong><br>
                    正在对目标发起DDoS攻击...
                </div>
            `;

        case 'completion':
            const successRate = logEntry.stats.totalRequests > 0
                ? ((logEntry.stats.successfulRequests / logEntry.stats.totalRequests) * 100).toFixed(1)
                : 0;

            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type monitor">攻击完成</span>
                        <span class="ms-2">${escapeHtml(logEntry.attackType)}</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-flag-checkered me-2"></i>攻击目标: ${escapeHtml(logEntry.endpoint)}
                </div>
                <div class="attack-details">
                    <div class="attack-metrics">
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.stats.totalRequests}</div>
                            <div class="metric-label">总请求数</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value text-success">${logEntry.stats.successfulRequests}</div>
                            <div class="metric-label">成功请求</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value text-danger">${logEntry.stats.failedRequests}</div>
                            <div class="metric-label">失败请求</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">${successRate}%</div>
                            <div class="metric-label">成功率</div>
                        </div>
                    </div>
                </div>
                <div class="attack-status success">
                    <i class="fas fa-check-circle me-2"></i>
                    <strong>攻击已完成</strong><br>
                    总耗时: ${(logEntry.duration / 1000).toFixed(2)}秒
                </div>
            `;

        case 'error':
            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type cpu">攻击错误</span>
                        <span class="ms-2">${escapeHtml(logEntry.attackType)}</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-exclamation-triangle me-2"></i>攻击目标: ${escapeHtml(logEntry.endpoint)}
                </div>
                <div class="attack-status danger">
                    <i class="fas fa-times-circle me-2"></i>
                    <strong>攻击失败</strong><br>
                    错误信息: ${escapeHtml(logEntry.error)}
                </div>
            `;

        case 'success':
            const responseTimeInfo = logEntry.responseTimeInfo || { displayTime: 'N/A', type: 'unknown' };
            const responseTimeDisplay = responseTimeInfo.displayTime;
            const responseTimeType = responseTimeInfo.type;

            // 根据响应时间类型确定样式类
            let timeStyleClass = 'response-time-unknown';
            if (responseTimeType === 'error') {
                timeStyleClass = 'response-time-error';
            } else if (responseTimeType.includes('delay')) {
                timeStyleClass = 'response-time-slow';
            } else if (responseTimeInfo.rawValue !== null) {
                if (responseTimeInfo.rawValue < 100) {
                    timeStyleClass = 'response-time-fast';
                } else if (responseTimeInfo.rawValue < 1000) {
                    timeStyleClass = 'response-time-normal';
                } else {
                    timeStyleClass = 'response-time-slow';
                }
            }

            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type flood">请求成功</span>
                        <span class="ms-2">线程${logEntry.threadId + 1}</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-check me-2"></i>目标端点: ${escapeHtml(logEntry.endpoint)}
                </div>
                <div class="attack-details">
                    <div class="request-details-row">
                        <span class="request-id">
                            <i class="fas fa-hashtag me-1"></i>请求ID: ${logEntry.response?.data?.request_id ?? 'N/A'}
                        </span>
                        <span class="response-time-display ${timeStyleClass}">
                            <i class="fas fa-clock me-1"></i>
                            <span class="response-time-value">${responseTimeDisplay}</span>
                        </span>
                    </div>
                    ${logEntry.response?.data?.warning ? `
                    <div class="attack-warning">
                        <i class="fas fa-exclamation-triangle me-1"></i>${escapeHtml(logEntry.response.data.warning)}
                    </div>` : ''}
                </div>
            `;

        case 'failure':
            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type cpu">请求失败</span>
                        <span class="ms-2">线程${logEntry.threadId + 1}</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-times me-2"></i>目标端点: ${escapeHtml(logEntry.endpoint)}
                </div>
                <div class="attack-status warning">
                    <i class="fas fa-exclamation-circle me-2"></i>
                    错误: ${escapeHtml(logEntry.error)}
                </div>
            `;

        case 'status':
            // 格式化显示状态信息
            const statusData = logEntry.data?.data || {};
            const targets = Array.isArray(statusData.available_targets) ? statusData.available_targets : [];

            let targetListHtml = '';
            if (targets.length > 0) {
                targetListHtml = `
                <div class="attack-target-list mt-3">
                    <h6 class="mb-2"><i class="fas fa-list me-2"></i>可用攻击目标:</h6>
                    ${targets.map(target => `
                    <div class="target-item p-2 mb-2 bg-light rounded">
                        <div class="d-flex justify-content-between align-items-center">
                            <span class="fw-medium">${escapeHtml(target.desc || target.path)}</span>
                            <span class="badge bg-secondary">${escapeHtml(target.method)}</span>
                        </div>
                        <div class="small text-muted mt-1">
                            <i class="fas fa-link me-1"></i>${escapeHtml(target.path)}
                        </div>
                        ${target.vulnerability ? `
                        <div class="small text-warning mt-1">
                            <i class="fas fa-exclamation-triangle me-1"></i>${escapeHtml(target.vulnerability)}
                        </div>` : ''}
                    </div>`).join('')}
                </div>`;
            }

            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type monitor">状态检查</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-target">
                    <i class="fas fa-server me-2"></i>DDoS攻击靶场状态
                </div>
                <div class="attack-details">
                    <div class="attack-metrics mb-3">
                        <div class="metric-card">
                            <div class="metric-value">${statusData.total_requests_received || 0}</div>
                            <div class="metric-label">总接收请求</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">${targets.length}</div>
                            <div class="metric-label">可用目标</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">
                                <i class="fas fa-shield-alt text-success"></i>
                            </div>
                            <div class="metric-label">安全环境</div>
                        </div>
                    </div>
                    ${targetListHtml}
                    ${statusData.security_warning ? `
                    <div class="alert alert-warning mt-3">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <strong>安全提醒:</strong> ${escapeHtml(statusData.security_warning)}
                    </div>` : ''}
                    ${statusData.tip ? `
                    <div class="alert alert-info mt-2">
                        <i class="fas fa-info-circle me-2"></i>
                        ${escapeHtml(statusData.tip)}
                    </div>` : ''}
                </div>
            `;

        case 'metrics':
            return `
                <div class="attack-header">
                    <div>
                        <span class="attack-type monitor">系统指标</span>
                    </div>
                    <div class="attack-timestamp">${logEntry.timestamp}</div>
                </div>
                <div class="attack-details">
                    <div class="attack-metrics">
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.metrics.totalRequests}</div>
                            <div class="metric-label">总接收请求</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.metrics.availableTargets}</div>
                            <div class="metric-label">可用攻击目标</div>
                        </div>
                        <div class="metric-card">
                            <div class="metric-value">${logEntry.metrics.timestamp}</div>
                            <div class="metric-label">检查时间</div>
                        </div>
                    </div>
                </div>
            `;

        default:
            return `<div>未知日志类型: ${logEntry.type}</div>`;
    }
}

/**
 * 状态管理函数
 * @param {string} status
 * @param {string} text
 */
function updateStatus(status, text) {
    const indicator = document.querySelector(DDOS_CONFIG.SELECTORS.STATUS_INDICATOR);
    if (indicator) {
        indicator.className = `badge ${getStatusClass(status)}`;
        indicator.innerHTML = `${getStatusIcon(status)} ${text}`;
    }
}

/**
 * @param {string} status
 * @returns {string}
 */
function getStatusClass(status) {
    const classes = {
        'executing': 'bg-warning status-executing',
        'ready': 'bg-secondary',
        'success': 'bg-success status-success',
        'error': 'bg-danger status-error'
    };
    return classes[status] || 'bg-secondary';
}

/**
 * @param {string} status
 * @returns {string}
 */
function getStatusIcon(status) {
    const icons = {
        'executing': '<i class="fas fa-circle-notch fa-spin"></i>',
        'ready': '<i class="fas fa-circle"></i>',
        'success': '<i class="fas fa-check"></i>',
        'error': '<i class="fas fa-times"></i>'
    };
    return icons[status] || '<i class="fas fa-circle"></i>';
}

/**
 * 进度条更新
 * @param {number} percent
 * @param {string} text
 */
function updateProgressBar(percent, text) {
    const progressBar = document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_PROGRESS);
    const progressText = document.querySelector(DDOS_CONFIG.SELECTORS.PROGRESS_TEXT);

    if (progressBar) {
        progressBar.style.width = `${percent}%`;
        progressBar.textContent = `${Math.round(percent)}%`;
    }

    if (progressText) {
        progressText.textContent = text;
    }
}

/**
 * 加载状态管理
 * @param {boolean} show
 */
function showLoading(show) {
    const overlay = document.querySelector(DDOS_CONFIG.SELECTORS.LOADING_OVERLAY);
    if (overlay) {
        overlay.classList.toggle('show', show);
    }
}

/**
 * 通知系统
 * @param {string} message
 * @param {string} type
 */
function showNotification(message, type = 'info') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${getAlertType(type)} alert-banner fade show`;
    alertDiv.role = 'alert';
    alertDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        z-index: 10000;
        min-width: 300px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    `;
    alertDiv.innerHTML = `
        <i class="${getAlertIcon(type)} me-2"></i>
        ${escapeHtml(message)}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;

    document.body.appendChild(alertDiv);

    // 3秒后自动移除
    setTimeout(() => {
        if (alertDiv.parentNode) {
            alertDiv.remove();
        }
    }, 3000);
}

/**
 * @param {string} type
 * @returns {string}
 */
function getAlertType(type) {
    const types = {
        'success': 'success',
        'error': 'danger',
        'warning': 'warning'
    };
    return types[type] || 'info';
}

/**
 * @param {string} type
 * @returns {string}
 */
function getAlertIcon(type) {
    const icons = {
        'success': 'fas fa-check-circle',
        'error': 'fas fa-exclamation-circle',
        'warning': 'fas fa-exclamation-triangle'
    };
    return icons[type] || 'fas fa-info-circle';
}

/**
 * HTML转义函数
 * @param {string} text
 * @returns {string}
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 清空攻击日志
 */
function clearAttackLog() {
    const container = document.querySelector(DDOS_CONFIG.SELECTORS.ATTACK_LOG_CONTAINER);
    if (container) {
        container.innerHTML = `
            <div class="welcome-message p-4 text-center text-muted">
                <i class="fas fa-bolt fa-3x mb-3"></i>
                <h4>攻击日志已清空</h4>
                <p class="mb-0">请重新开始攻击测试</p>
            </div>
        `;
    }
    ddosState.attackLogs = [];
    showNotification('攻击日志已清空', 'info');
}

/**
 * 导出攻击报告
 */
async function exportAttackReport() {
    try {
        // 获取最新的总请求数
        const response = await fetch(DDOS_CONFIG.ENDPOINTS.STATUS);
        /** @type {ApiResponse} */
        const statusData = await response.json();
        const totalReceivedRequests = statusData?.data?.total_requests_received || 0;

        const reportContent = `
DDoS攻击测试报告
========================

生成时间: ${new Date().toLocaleString('zh-CN')}
测试环境: DDoS攻击模拟靶场

总体统计:
- 累计接收请求: ${totalReceivedRequests}
- 当前会话攻击统计:
  - 总请求数: ${ddosState.attackStats.totalRequests}
  - 成功请求数: ${ddosState.attackStats.successfulRequests}
  - 失败请求数: ${ddosState.attackStats.failedRequests}
  - 成功率: ${ddosState.attackStats.totalRequests > 0 ? ((ddosState.attackStats.successfulRequests / ddosState.attackStats.totalRequests) * 100).toFixed(1) : 0}%

详细日志:
${ddosState.attackLogs.map(log => `- [${log.timestamp}] ${log.type}: ${JSON.stringify(log, null, 2)}`).join('\n')}

---
报告生成完毕
        `.trim();

        // 创建下载链接
        const blob = new Blob([reportContent], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `ddos-attack-report-${new Date().getTime()}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        showNotification('攻击报告已导出', 'success');
    } catch (error) {
        console.error('导出报告失败:', error);
        showNotification(`导出报告失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}

/**
 * 响应时间计算函数
 * @param {ApiResponse} responseData
 * @returns {{displayTime: string, type: string, rawValue: number|null, unit: string}}
 */
function calculateResponseTime(responseData) {
    if (!responseData || !responseData.data) {
        return { displayTime: 'N/A', type: 'unknown', rawValue: null, unit: 'unknown' };
    }

    const data = responseData.data;
    let responseTime = null;
    let timeType = 'unknown';

    // 按优先级尝试获取不同的时间字段
    if (data.server_response_time_ms !== undefined) {
        responseTime = data.server_response_time_ms;
        timeType = 'server_response';
    } else if (data.server_processing_time_ms !== undefined) {
        responseTime = data.server_processing_time_ms;
        timeType = 'server_processing';
    } else if (data.cost_time_ms !== undefined) {
        responseTime = data.cost_time_ms;
        timeType = 'cost_time';
    } else if (data.actual_cost_time_ms !== undefined) {
        responseTime = data.actual_cost_time_ms;
        timeType = 'actual_cost';
    } else if (data.simulated_delay_ms !== undefined) {
        responseTime = data.simulated_delay_ms;
        timeType = 'simulated_delay';
    }

    // 格式化显示时间
    let displayTime;
    if (responseTime === null) {
        displayTime = 'N/A';
    } else if (responseTime < 1) {
        displayTime = '<1ms';
    } else if (responseTime < 1000) {
        displayTime = `${Math.round(responseTime)}ms`;
    } else {
        displayTime = `${(responseTime / 1000).toFixed(2)}s`;
    }

    return {
        displayTime: displayTime,
        type: timeType,
        rawValue: responseTime,
        unit: responseTime !== null && responseTime >= 1000 ? 'seconds' : 'milliseconds'
    };
}

// 新增带配置的批量攻击函数（修复Promise被忽略问题）
async function executeBatchAttackWithConfig() {
    const requestCount = parseInt(document.querySelector(DDOS_CONFIG.SELECTORS.BATCH_REQUEST_COUNT)?.value || '50');
    try {
        await executeBatchAttack('/target/ddos/compute-heavy', requestCount, `批量CPU攻击(${requestCount}请求)`);
    } catch (error) {
        console.error('批量攻击执行失败:', error);
        showNotification(`批量攻击执行失败: ${error instanceof Error ? error.message : String(error)}`, 'error');
    }
}

// 暴露全局函数供HTML调用
window.executeDdosAttack = executeDdosAttack;
window.executeBatchAttack = executeBatchAttack;
window.executeBatchAttackWithConfig = executeBatchAttackWithConfig;
window.executeHighFrequencyAttack = executeHighFrequencyAttack;
window.executeStressTest = executeStressTest;
window.checkAttackStatus = checkAttackStatus;
window.getSystemMetrics = getSystemMetrics;
window.clearAttackLog = clearAttackLog;
window.exportAttackReport = exportAttackReport;