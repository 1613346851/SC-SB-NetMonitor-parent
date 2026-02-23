/**
 * DDoS攻击模拟靶场JavaScript逻辑
 * 实现前后端交互、攻击测试、结果展示等功能
 */

// 全局变量
let attackInProgress = false;
let attackAbortController = null;
let attackStats = {
    totalRequests: 0,
    successfulRequests: 0,
    failedRequests: 0,
    startTime: null,
    endTime: null
};

// DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

/**
 * 应用初始化函数
 */
function initializeApp() {
    // 绑定事件监听器
    bindEventListeners();
    
    // 初始化系统状态
    updateSystemStatus();
    
    // 初始化UI状态
    updateStatus('ready', '就绪');
    
    // 设置线程数滑块显示
    setupThreadSlider();
    
    console.log('DDoS攻击模拟靶场初始化完成');
}

/**
 * 绑定事件监听器
 */
function bindEventListeners() {
    // 自定义攻击按钮
    document.getElementById('startCustomAttackBtn').addEventListener('click', startCustomAttack);
    document.getElementById('stopAttackBtn').addEventListener('click', stopAttack);
    
    // 线程数滑块变化监听
    document.getElementById('concurrentThreads').addEventListener('input', function() {
        document.getElementById('threadCountDisplay').textContent = this.value;
    });
    
    // 输出容器滚动到底部
    const outputContainer = document.getElementById('outputContainer');
    if (outputContainer) {
        const observer = new MutationObserver(() => {
            outputContainer.scrollTop = outputContainer.scrollHeight;
        });
        observer.observe(outputContainer, { childList: true, subtree: true });
    }
    
    // 定期更新系统状态
    setInterval(updateSystemStatus, 5000);
}

/**
 * 设置线程数滑块显示
 */
function setupThreadSlider() {
    const slider = document.getElementById('concurrentThreads');
    const display = document.getElementById('threadCountDisplay');
    slider.addEventListener('input', function() {
        display.textContent = this.value;
    });
}

/**
 * 更新系统状态信息
 */
async function updateSystemStatus() {
    try {
        const response = await fetch('/target/ddos/status');
        if (response.ok) {
            const data = await response.json();
            if (data.data && data.data.total_requests_received !== undefined) {
                document.getElementById('totalRequests').textContent = data.data.total_requests_received;
                attackStats.totalRequests = data.data.total_requests_received;
            }
        }
    } catch (error) {
        console.warn('无法获取系统状态:', error);
    }
    
    // 模拟CPU和内存使用率
    updateSimulatedMetrics();
}

/**
 * 更新模拟的系统指标
 */
function updateSimulatedMetrics() {
    // 模拟CPU使用率 (0-100%)
    const cpuUsage = Math.floor(Math.random() * 30) + 20; // 20-50%
    document.getElementById('cpuUsage').textContent = cpuUsage + '%';
    
    // 模拟内存使用 (100-500MB)
    const memoryUsage = Math.floor(Math.random() * 400) + 100; // 100-500MB
    document.getElementById('memoryUsage').textContent = memoryUsage + 'MB';
}

/**
 * 执行单个DDoS测试用例
 */
async function executeDdosTest(endpoint, testCaseId) {
    if (attackInProgress) {
        showNotification('请先停止当前攻击再执行新的测试', 'warning');
        return;
    }
    
    showLoading(true);
    updateStatus('executing', `执行测试 ${testCaseId}...`);
    
    try {
        const startTime = Date.now();
        const response = await fetch(endpoint);
        const endTime = Date.now();
        const responseTime = endTime - startTime;
        
        let resultData;
        if (response.ok) {
            const data = await response.json();
            resultData = {
                testCaseId: testCaseId,
                endpoint: endpoint,
                success: true,
                responseTime: responseTime,
                statusCode: response.status,
                data: data,
                timestamp: new Date().toLocaleString('zh-CN')
            };
        } else {
            resultData = {
                testCaseId: testCaseId,
                endpoint: endpoint,
                success: false,
                responseTime: responseTime,
                statusCode: response.status,
                error: await response.text(),
                timestamp: new Date().toLocaleString('zh-CN')
            };
        }
        
        displayAttackResult(resultData);
        showNotification(`测试 ${testCaseId} 执行完成`, 'success');
        
    } catch (error) {
        const errorData = {
            testCaseId: testCaseId,
            endpoint: endpoint,
            success: false,
            error: error.message,
            timestamp: new Date().toLocaleString('zh-CN')
        };
        displayAttackResult(errorData);
        showNotification(`测试 ${testCaseId} 执行失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
        updateSystemStatus();
    }
}

/**
 * 执行多次调用测试
 */
async function executeMultipleCalls(endpoint, count, testCaseId) {
    if (attackInProgress) {
        showNotification('请先停止当前攻击再执行新的测试', 'warning');
        return;
    }
    
    showLoading(true);
    updateStatus('executing', `执行测试 ${testCaseId} (${count}次)...`);
    
    const results = [];
    let successCount = 0;
    let failCount = 0;
    
    for (let i = 1; i <= count; i++) {
        try {
            const startTime = Date.now();
            const response = await fetch(endpoint);
            const endTime = Date.now();
            
            if (response.ok) {
                const data = await response.json();
                results.push({
                    callNumber: i,
                    success: true,
                    responseTime: endTime - startTime,
                    data: data
                });
                successCount++;
            } else {
                results.push({
                    callNumber: i,
                    success: false,
                    responseTime: endTime - startTime,
                    error: await response.text()
                });
                failCount++;
            }
        } catch (error) {
            results.push({
                callNumber: i,
                success: false,
                error: error.message
            });
            failCount++;
        }
    }
    
    // 显示汇总结果
    const summaryData = {
        testCaseId: testCaseId,
        endpoint: endpoint,
        type: 'multiple_calls',
        totalCount: count,
        successCount: successCount,
        failCount: failCount,
        results: results,
        timestamp: new Date().toLocaleString('zh-CN')
    };
    
    displayAttackResult(summaryData);
    showNotification(`测试 ${testCaseId} 完成: ${successCount}/${count} 成功`, 
        successCount === count ? 'success' : 'warning');
    
    showLoading(false);
    updateStatus('ready', '就绪');
    updateSystemStatus();
}

/**
 * 开始压力测试
 */
async function startStressTest(endpoint, concurrentThreads, durationSeconds, testCaseId) {
    if (attackInProgress) {
        showNotification('攻击已在进行中', 'warning');
        return;
    }
    
    showLoading(true);
    updateStatus('executing', `执行压力测试 ${testCaseId}...`);
    
    attackInProgress = true;
    attackAbortController = new AbortController();
    attackStats = {
        totalRequests: 0,
        successfulRequests: 0,
        failedRequests: 0,
        startTime: Date.now(),
        endTime: Date.now() + (durationSeconds * 1000)
    };
    
    updateAttackButtons(true);
    
    try {
        // 更新进度条
        updateAttackProgress(0, `启动 ${concurrentThreads} 个并发线程...`);
        
        // 创建并发请求
        const promises = [];
        for (let i = 0; i < concurrentThreads; i++) {
            promises.push(sendContinuousRequests(endpoint, durationSeconds, i + 1));
        }
        
        await Promise.all(promises);
        
        // 显示最终结果
        const finalStats = {
            testCaseId: testCaseId,
            endpoint: endpoint,
            type: 'stress_test',
            concurrentThreads: concurrentThreads,
            durationSeconds: durationSeconds,
            stats: { ...attackStats },
            timestamp: new Date().toLocaleString('zh-CN')
        };
        
        displayAttackResult(finalStats);
        showNotification(`压力测试 ${testCaseId} 完成`, 'success');
        
    } catch (error) {
        if (error.name !== 'AbortError') {
            showNotification(`压力测试出错: ${error.message}`, 'error');
        }
    } finally {
        attackInProgress = false;
        attackAbortController = null;
        showLoading(false);
        updateStatus('ready', '就绪');
        updateAttackButtons(false);
        updateSystemStatus();
    }
}

/**
 * 发送持续请求（用于压力测试）
 */
async function sendContinuousRequests(endpoint, durationSeconds, threadId) {
    const endTime = Date.now() + (durationSeconds * 1000);
    
    while (Date.now() < endTime && attackInProgress) {
        try {
            const startTime = Date.now();
            const response = await fetch(endpoint, {
                signal: attackAbortController.signal
            });
            const responseTime = Date.now() - startTime;
            
            attackStats.totalRequests++;
            if (response.ok) {
                attackStats.successfulRequests++;
            } else {
                attackStats.failedRequests++;
            }
            
            // 更新进度（每10个请求更新一次以提高性能）
            if (attackStats.totalRequests % 10 === 0) {
                const elapsed = Date.now() - attackStats.startTime;
                const progress = Math.min((elapsed / (durationSeconds * 1000)) * 100, 100);
                updateAttackProgress(progress, `线程${threadId}: ${attackStats.totalRequests} 请求`);
            }
            
        } catch (error) {
            if (error.name === 'AbortError') {
                break;
            }
            attackStats.totalRequests++;
            attackStats.failedRequests++;
        }
        
        // 短暂延迟避免过于频繁的请求
        await new Promise(resolve => setTimeout(resolve, 10));
    }
}

/**
 * 开始连接测试
 */
async function startConnectionTest(endpoint, concurrentConnections, testCaseId) {
    if (attackInProgress) {
        showNotification('请先停止当前攻击再执行新的测试', 'warning');
        return;
    }
    
    showLoading(true);
    updateStatus('executing', `执行连接测试 ${testCaseId}...`);
    
    const startTime = Date.now();
    const promises = [];
    
    for (let i = 0; i < concurrentConnections; i++) {
        promises.push(fetch(endpoint));
    }
    
    try {
        const responses = await Promise.all(promises);
        const endTime = Date.now();
        const totalTime = endTime - startTime;
        
        const successCount = responses.filter(r => r.ok).length;
        const failCount = responses.length - successCount;
        
        const resultData = {
            testCaseId: testCaseId,
            endpoint: endpoint,
            type: 'connection_test',
            concurrentConnections: concurrentConnections,
            totalTime: totalTime,
            successCount: successCount,
            failCount: failCount,
            successRate: ((successCount / concurrentConnections) * 100).toFixed(1),
            timestamp: new Date().toLocaleString('zh-CN')
        };
        
        displayAttackResult(resultData);
        showNotification(`连接测试 ${testCaseId} 完成: ${successCount}/${concurrentConnections} 成功`, 
            successCount === concurrentConnections ? 'success' : 'warning');
        
    } catch (error) {
        showNotification(`连接测试失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
        updateSystemStatus();
    }
}

/**
 * 开始高频测试
 */
async function startHighFrequencyTest(endpoint, qps, durationSeconds, testCaseId) {
    if (attackInProgress) {
        showNotification('攻击已在进行中', 'warning');
        return;
    }
    
    showLoading(true);
    updateStatus('executing', `执行高频测试 ${testCaseId}...`);
    
    attackInProgress = true;
    attackAbortController = new AbortController();
    const requestsPerSecond = qps;
    const interval = 1000 / requestsPerSecond;
    
    attackStats = {
        totalRequests: 0,
        successfulRequests: 0,
        failedRequests: 0,
        startTime: Date.now(),
        endTime: Date.now() + (durationSeconds * 1000)
    };
    
    updateAttackButtons(true);
    
    try {
        const timer = setInterval(async () => {
            if (!attackInProgress || Date.now() >= attackStats.endTime) {
                clearInterval(timer);
                return;
            }
            
            try {
                const response = await fetch(endpoint, {
                    signal: attackAbortController.signal
                });
                
                attackStats.totalRequests++;
                if (response.ok) {
                    attackStats.successfulRequests++;
                } else {
                    attackStats.failedRequests++;
                }
                
                // 更新统计信息
                if (attackStats.totalRequests % 100 === 0) {
                    const elapsed = (Date.now() - attackStats.startTime) / 1000;
                    const currentQPS = Math.round(attackStats.totalRequests / elapsed);
                    updateAttackProgress(
                        Math.min((elapsed / durationSeconds) * 100, 100),
                        `QPS: ${currentQPS}/${requestsPerSecond}, 总请求数: ${attackStats.totalRequests}`
                    );
                }
                
            } catch (error) {
                if (error.name !== 'AbortError') {
                    attackStats.totalRequests++;
                    attackStats.failedRequests++;
                }
            }
        }, interval);
        
        // 等待测试完成
        await new Promise(resolve => {
            const checkCompletion = () => {
                if (!attackInProgress || Date.now() >= attackStats.endTime) {
                    clearInterval(timer);
                    resolve();
                } else {
                    setTimeout(checkCompletion, 100);
                }
            };
            checkCompletion();
        });
        
        // 显示最终结果
        const finalStats = {
            testCaseId: testCaseId,
            endpoint: endpoint,
            type: 'high_frequency_test',
            targetQPS: qps,
            actualDuration: (Date.now() - attackStats.startTime) / 1000,
            stats: { ...attackStats },
            timestamp: new Date().toLocaleString('zh-CN')
        };
        
        displayAttackResult(finalStats);
        showNotification(`高频测试 ${testCaseId} 完成`, 'success');
        
    } catch (error) {
        if (error.name !== 'AbortError') {
            showNotification(`高频测试出错: ${error.message}`, 'error');
        }
    } finally {
        attackInProgress = false;
        attackAbortController = null;
        showLoading(false);
        updateStatus('ready', '就绪');
        updateAttackButtons(false);
        updateSystemStatus();
    }
}

/**
 * 开始极限测试
 */
async function startExtremeTest(endpoint, maxQPS, testCaseId) {
    showNotification('警告：即将执行极限测试，可能导致服务不稳定！', 'warning');
    
    // 确认执行
    if (!confirm('确定要执行极限测试吗？这可能会对服务造成严重影响。')) {
        return;
    }
    
    await startHighFrequencyTest(endpoint, maxQPS, 30, testCaseId); // 30秒极限测试
}

/**
 * 开始自定义攻击
 */
async function startCustomAttack() {
    if (attackInProgress) {
        showNotification('攻击已在进行中', 'warning');
        return;
    }
    
    const endpoint = document.getElementById('customEndpoint').value;
    const threads = parseInt(document.getElementById('concurrentThreads').value);
    const duration = parseInt(document.getElementById('attackDuration').value);
    
    if (!endpoint || threads <= 0 || duration <= 0) {
        showNotification('请填写完整的攻击参数', 'warning');
        return;
    }
    
    await startStressTest(endpoint, threads, duration, 'CUSTOM');
}

/**
 * 停止攻击
 */
function stopAttack() {
    if (attackInProgress && attackAbortController) {
        attackAbortController.abort();
        attackInProgress = false;
        showNotification('攻击已停止', 'info');
    }
}

/**
 * 更新攻击按钮状态
 */
function updateAttackButtons(attacking) {
    const startBtn = document.getElementById('startCustomAttackBtn');
    const stopBtn = document.getElementById('stopAttackBtn');
    
    if (attacking) {
        startBtn.disabled = true;
        stopBtn.disabled = false;
    } else {
        startBtn.disabled = false;
        stopBtn.disabled = true;
    }
}

/**
 * 更新攻击进度
 */
function updateAttackProgress(percent, info) {
    const progressBar = document.getElementById('attackProgressBar');
    const progressText = document.getElementById('progressText');
    const progressInfo = document.getElementById('progressInfo');
    
    if (progressBar) {
        progressBar.style.width = percent + '%';
        progressText.textContent = Math.round(percent) + '%';
    }
    
    if (progressInfo) {
        progressInfo.textContent = info;
    }
}

/**
 * 测试一致性
 */
async function testConsistency(testCaseId) {
    showLoading(true);
    updateStatus('executing', `执行一致性测试 ${testCaseId}...`);
    
    try {
        // 先调用几次不同的接口
        await fetch('/target/ddos/ping');
        await fetch('/target/ddos/ping');
        await fetch('/target/ddos/io-delay');
        
        // 查询状态
        const response = await fetch('/target/ddos/status');
        const data = await response.json();
        
        const resultData = {
            testCaseId: testCaseId,
            type: 'consistency_test',
            totalRequestsReceived: data.data.total_requests_received,
            expectedCount: 3,
            isConsistent: data.data.total_requests_received === 3,
            timestamp: new Date().toLocaleString('zh-CN')
        };
        
        displayAttackResult(resultData);
        showNotification(
            `一致性测试完成: ${resultData.isConsistent ? '通过' : '失败'}`, 
            resultData.isConsistent ? 'success' : 'error'
        );
        
    } catch (error) {
        showNotification(`一致性测试失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
        updateSystemStatus();
    }
}

/**
 * 测试并发一致性
 */
async function testConcurrentCalls(testCaseId) {
    showLoading(true);
    updateStatus('executing', `执行并发一致性测试 ${testCaseId}...`);
    
    try {
        const threadCount = 100;
        const promises = [];
        
        // 同时调用不同接口
        for (let i = 0; i < threadCount; i++) {
            if (i % 3 === 0) {
                promises.push(fetch('/target/ddos/ping'));
            } else if (i % 3 === 1) {
                promises.push(fetch('/target/ddos/io-delay'));
            } else {
                promises.push(fetch('/target/ddos/compute-heavy'));
            }
        }
        
        await Promise.all(promises);
        
        // 查询最终状态
        const response = await fetch('/target/ddos/status');
        const data = await response.json();
        
        const resultData = {
            testCaseId: testCaseId,
            type: 'concurrent_consistency_test',
            threadCount: threadCount,
            totalRequestsReceived: data.data.total_requests_received,
            isConsistent: data.data.total_requests_received === threadCount,
            timestamp: new Date().toLocaleString('zh-CN')
        };
        
        displayAttackResult(resultData);
        showNotification(
            `并发一致性测试完成: ${resultData.isConsistent ? '通过' : '失败'}`, 
            resultData.isConsistent ? 'success' : 'error'
        );
        
    } catch (error) {
        showNotification(`并发一致性测试失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
        updateSystemStatus();
    }
}

/**
 * 显示攻击结果
 */
function displayAttackResult(resultData) {
    const outputContainer = document.getElementById('outputContainer');
    
    // 移除欢迎消息（如果存在）
    const welcomeMessage = outputContainer.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }
    
    // 创建结果元素
    const resultElement = document.createElement('div');
    resultElement.className = 'attack-result';
    
    // 构建HTML内容
    resultElement.innerHTML = `
        <div class="attack-header">
            <div>
                <span class="badge bg-danger">攻击测试</span>
                <span class="ms-2">测试用例: ${escapeHtml(resultData.testCaseId || '自定义')}</span>
                ${resultData.endpoint ? `<span class="ms-2 text-muted">端点: ${escapeHtml(resultData.endpoint)}</span>` : ''}
            </div>
            <div class="attack-timestamp">${resultData.timestamp}</div>
        </div>
        <div class="attack-result-content">
            ${formatAttackResult(resultData)}
        </div>
        ${resultData.type ? `
        <div class="attack-details">
            <small class="text-muted">
                <i class="fas fa-info-circle me-1"></i>
                测试类型: ${getTestTypeDescription(resultData.type)}
            </small>
        </div>
        ` : ''}
    `;
    
    // 添加到输出容器
    outputContainer.appendChild(resultElement);
    
    // 滚动到最新结果
    outputContainer.scrollTop = outputContainer.scrollHeight;
}

/**
 * 获取测试类型描述
 */
function getTestTypeDescription(type) {
    const descriptions = {
        'multiple_calls': '多次调用测试',
        'stress_test': '压力测试',
        'connection_test': '连接测试',
        'high_frequency_test': '高频测试',
        'consistency_test': '一致性测试',
        'concurrent_consistency_test': '并发一致性测试'
    };
    return descriptions[type] || type;
}

/**
 * 格式化攻击结果显示
 */
function formatAttackResult(resultData) {
    let resultText = '';
    
    if (resultData.type === 'multiple_calls') {
        resultText += `📊 多次调用测试结果:\n`;
        resultText += `总计调用: ${resultData.totalCount} 次\n`;
        resultText += `成功: ${resultData.successCount} 次\n`;
        resultText += `失败: ${resultData.failCount} 次\n`;
        resultText += `成功率: ${((resultData.successCount / resultData.totalCount) * 100).toFixed(1)}%\n\n`;
        
        // 显示最后几次调用的详细信息
        const recentResults = resultData.results.slice(-3);
        resultText += `最近调用详情:\n`;
        recentResults.forEach(r => {
            resultText += `第${r.callNumber}次: ${r.success ? '✅' : '❌'} (${r.responseTime}ms)\n`;
        });
        
    } else if (resultData.type === 'stress_test') {
        resultText += `🔥 压力测试结果:\n`;
        resultText += `并发线程数: ${resultData.concurrentThreads}\n`;
        resultText += `测试时长: ${resultData.durationSeconds} 秒\n`;
        resultText += `总请求数: ${resultData.stats.totalRequests}\n`;
        resultText += `成功请求数: ${resultData.stats.successfulRequests}\n`;
        resultText += `失败请求数: ${resultData.stats.failedRequests}\n`;
        resultText += `成功率: ${((resultData.stats.successfulRequests / resultData.stats.totalRequests) * 100).toFixed(1)}%\n`;
        
    } else if (resultData.type === 'connection_test') {
        resultText += `🔗 连接测试结果:\n`;
        resultText += `并发连接数: ${resultData.concurrentConnections}\n`;
        resultText += `总耗时: ${resultData.totalTime}ms\n`;
        resultText += `成功连接: ${resultData.successCount}\n`;
        resultText += `失败连接: ${resultData.failCount}\n`;
        resultText += `成功率: ${resultData.successRate}%\n`;
        
    } else if (resultData.type === 'high_frequency_test') {
        resultText += `⚡ 高频测试结果:\n`;
        resultText += `目标QPS: ${resultData.targetQPS}\n`;
        resultText += `实际运行时间: ${resultData.actualDuration.toFixed(1)} 秒\n`;
        resultText += `总请求数: ${resultData.stats.totalRequests}\n`;
        resultText += `成功请求数: ${resultData.stats.successfulRequests}\n`;
        resultText += `失败请求数: ${resultData.stats.failedRequests}\n`;
        resultText += `实际QPS: ${(resultData.stats.totalRequests / resultData.actualDuration).toFixed(1)}\n`;
        resultText += `成功率: ${((resultData.stats.successfulRequests / resultData.stats.totalRequests) * 100).toFixed(1)}%\n`;
        
    } else if (resultData.type === 'consistency_test') {
        resultText += `⚖️ 一致性测试结果:\n`;
        resultText += `期望请求数: ${resultData.expectedCount}\n`;
        resultText += `实际接收数: ${resultData.totalRequestsReceived}\n`;
        resultText += `测试结果: ${resultData.isConsistent ? '✅ 通过' : '❌ 失败'}\n`;
        
    } else if (resultData.type === 'concurrent_consistency_test') {
        resultText += `👥 并发一致性测试结果:\n`;
        resultText += `并发线程数: ${resultData.threadCount}\n`;
        resultText += `实际接收数: ${resultData.totalRequestsReceived}\n`;
        resultText += `测试结果: ${resultData.isConsistent ? '✅ 通过' : '❌ 失败'}\n`;
        
    } else {
        // 单次调用结果
        if (resultData.success) {
            resultText += `✅ 测试成功\n`;
            resultText += `响应时间: ${resultData.responseTime}ms\n`;
            resultText += `状态码: ${resultData.statusCode}\n\n`;
            
            if (resultData.data) {
                resultText += `响应数据:\n${escapeHtml(JSON.stringify(resultData.data, null, 2))}\n`;
            }
        } else {
            resultText += `❌ 测试失败\n`;
            resultText += `错误信息: ${escapeHtml(resultData.error || '未知错误')}\n`;
            if (resultData.statusCode) {
                resultText += `状态码: ${resultData.statusCode}\n`;
            }
        }
    }
    
    return resultText;
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
 * 更新状态指示器
 */
function updateStatus(status, text) {
    const statusIndicator = document.getElementById('statusIndicator');
    if (statusIndicator) {
        statusIndicator.className = `badge ${getStatusClass(status)}`;
        statusIndicator.innerHTML = getStatusIcon(status) + ' ' + text;
    }
}

/**
 * 获取状态样式类
 */
function getStatusClass(status) {
    switch (status) {
        case 'executing': return 'bg-warning status-executing';
        case 'success': return 'bg-success status-success';
        case 'error': return 'bg-danger status-error';
        default: return 'bg-secondary';
    }
}

/**
 * 获取状态图标
 */
function getStatusIcon(status) {
    switch (status) {
        case 'executing': return '<i class="fas fa-circle-notch fa-spin"></i>';
        case 'success': return '<i class="fas fa-check"></i>';
        case 'error': return '<i class="fas fa-times"></i>';
        default: return '<i class="fas fa-circle"></i>';
    }
}

/**
 * 显示加载遮罩
 */
function showLoading(show) {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) {
        if (show) {
            overlay.classList.add('show');
        } else {
            overlay.classList.remove('show');
        }
    }
}

/**
 * 显示通知消息
 */
function showNotification(message, type = 'info') {
    // 移除已存在的通知
    const existing = document.querySelector('.alert-banner');
    if (existing) {
        existing.remove();
    }

    // 创建通知元素
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert-banner alert alert-${getAlertType(type)}`;
    alertDiv.style.position = 'fixed';
    alertDiv.style.top = '70px';
    alertDiv.style.right = '20px';
    alertDiv.style.maxWidth = '400px';
    alertDiv.style.zIndex = '9999';
    alertDiv.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
    alertDiv.style.animation = 'slideInRight 0.3s ease-out';
    alertDiv.innerHTML = `
        <i class="${getAlertIcon(type)} me-2"></i>
        ${escapeHtml(message)}
        <button type="button" class="btn-close" style="float:right; margin-left:8px;" onclick="this.parentElement.remove()">
            <span>&times;</span>
        </button>
    `;

    document.body.insertBefore(alertDiv, document.body.firstChild);

    // 3秒后自动移除
    setTimeout(() => {
        if (alertDiv.parentNode) {
            alertDiv.remove();
        }
    }, 3000);
}

/**
 * 获取通知类型对应的Bootstrap类
 */
function getAlertType(type) {
    switch (type) {
        case 'success': return 'success';
        case 'error': return 'danger';
        case 'warning': return 'warning';
        default: return 'info';
    }
}

/**
 * 获取通知图标
 */
function getAlertIcon(type) {
    switch (type) {
        case 'success': return 'fas fa-check-circle';
        case 'error': return 'fas fa-exclamation-circle';
        case 'warning': return 'fas fa-exclamation-triangle';
        default: return 'fas fa-info-circle';
    }
}

/**
 * 清空输出
 */
function clearOutput() {
    const outputContainer = document.getElementById('outputContainer');
    if (outputContainer) {
        outputContainer.innerHTML = `
            <div class="welcome-message text-center text-muted d-flex flex-column justify-content-center align-items-center h-100">
                <i class="fas fa-bolt fa-4x mb-4 text-danger"></i>
                <h4 class="fw-bold text-dark">输出已清空</h4>
                <p class="mb-0 lead">请选择左侧测试用例开始DDoS攻击测试</p>
            </div>
        `;
        showNotification('输出已清空', 'info');
    }
}

// 暴露全局函数供HTML调用
window.executeDdosTest = executeDdosTest;
window.executeMultipleCalls = executeMultipleCalls;
window.startStressTest = startStressTest;
window.startConnectionTest = startConnectionTest;
window.startHighFrequencyTest = startHighFrequencyTest;
window.startExtremeTest = startExtremeTest;
window.testConsistency = testConsistency;
window.testConcurrentCalls = testConcurrentCalls;
window.clearOutput = clearOutput;
