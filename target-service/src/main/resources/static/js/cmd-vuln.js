/**
 * CMD命令注入漏洞测试平台JavaScript逻辑
 * 实现前后端交互、命令执行、结果展示等功能
 */

// 全局变量
let commandHistory = [];
let currentHistoryIndex = -1;
const API_ENDPOINT = '/target/cmd/execute';

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
    
    // 检测系统信息
    detectSystemInfo();
    
    // 初始化UI状态
    updateStatus('ready', '就绪');
    
    console.log('CMD漏洞测试平台初始化完成');
}

/**
 * 绑定事件监听器
 */
function bindEventListeners() {
    // 执行按钮点击事件
    document.getElementById('executeBtn').addEventListener('click', executeCommand);
    
    // 回车键执行命令
    document.getElementById('commandInput').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            executeCommand();
        }
    });
    
    // 命令输入框历史记录导航
    document.getElementById('commandInput').addEventListener('keydown', handleHistoryNavigation);
    
    // 输出容器滚动到底部
    const outputContainer = document.getElementById('outputContainer');
    if (outputContainer) {
        const observer = new MutationObserver(() => {
            outputContainer.scrollTop = outputContainer.scrollHeight;
        });
        observer.observe(outputContainer, { childList: true, subtree: true });
    }
}

/**
 * 检测系统信息
 */
function detectSystemInfo() {
    // 模拟检测操作系统信息
    const userAgent = navigator.userAgent.toLowerCase();
    let osInfo = '未知系统';
    
    if (userAgent.includes('win')) {
        osInfo = 'Windows (客户端)';
    } else if (userAgent.includes('mac')) {
        osInfo = 'macOS (客户端)';
    } else if (userAgent.includes('linux')) {
        osInfo = 'Linux (客户端)';
    }
    
    document.getElementById('osInfo').textContent = osInfo;
    
    // 显示API端点信息
    console.log('API端点:', API_ENDPOINT);
}

/**
 * 执行命令主函数
 */
function executeCommand() {
    const commandInput = document.getElementById('commandInput');
    const command = commandInput.value.trim();
    
    // 输入验证
    if (!command) {
        showNotification('请输入要执行的命令', 'warning');
        commandInput.focus();
        return;
    }
    
    // 添加到历史记录
    addToHistory(command);
    
    // 显示加载状态
    showLoading(true);
    updateStatus('executing', '执行中...');
    
    // 发送请求到后端
    sendCommandRequest(command)
        .then(response => {
            handleCommandResponse(command, response);
        })
        .catch(error => {
            handleCommandError(command, error);
        })
        .finally(() => {
            showLoading(false);
            updateStatus('ready', '就绪');
            commandInput.value = '';
        });
}

/**
 * 发送命令请求到后端API
 */
async function sendCommandRequest(command) {
    const url = `${API_ENDPOINT}?cmd=${encodeURIComponent(command)}`;
    
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
        
        return await response.json();
    } catch (error) {
        // 如果JSON解析失败，尝试获取文本响应
        try {
            const textResponse = await response.text();
            throw new Error(`服务器响应: ${textResponse}`);
        } catch (textError) {
            throw error;
        }
    }
}

/**
 * 处理命令响应
 */
function handleCommandResponse(command, response) {
    console.log('命令执行响应:', response);
    
    // 解析响应数据
    const resultData = {
        command: command,
        timestamp: new Date().toLocaleString('zh-CN'),
        success: response.code === 200 || !response.code,
        message: response.message || response.msg || '命令执行完成',
        data: response.data || {},
        rawResponse: response
    };
    
    // 显示结果
    displayCommandResult(resultData);
    
    // 显示通知
    if (resultData.success) {
        showNotification(`命令执行成功: ${command}`, 'success');
    } else {
        showNotification(`命令执行失败: ${resultData.message}`, 'error');
    }
}

/**
 * 处理命令执行错误
 */
function handleCommandError(command, error) {
    console.error('命令执行错误:', error);
    
    const errorData = {
        command: command,
        timestamp: new Date().toLocaleString('zh-CN'),
        success: false,
        message: error.message || '网络请求失败',
        error: error,
        isError: true
    };
    
    displayCommandResult(errorData);
    showNotification(`执行出错: ${error.message}`, 'error');
}

/**
 * 显示命令执行结果
 */
function displayCommandResult(resultData) {
    const outputContainer = document.getElementById('outputContainer');
    
    // 移除欢迎消息（如果存在）
    const welcomeMessage = outputContainer.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }
    
    // 创建结果元素
    const resultElement = document.createElement('div');
    resultElement.className = 'command-output';
    
    // 构建HTML内容
    resultElement.innerHTML = `
        <div class="command-header">
            <div>
                <span class="badge bg-primary">命令执行</span>
                <span class="ms-2">输入: ${escapeHtml(resultData.command)}</span>
            </div>
            <div class="command-timestamp">${resultData.timestamp}</div>
        </div>
        <div class="command-input">
            <i class="fas fa-terminal me-2"></i>执行命令: ${escapeHtml(resultData.command)}
        </div>
        <div class="command-result ${getResultClass(resultData)}">
            ${formatCommandResult(resultData)}
        </div>
        ${resultData.isError ? '' : `
        <div class="command-details">
            <small class="text-muted">
                <i class="fas fa-info-circle me-1"></i>
                ${resultData.success ? '✅ 命令执行成功' : '❌ 命令执行失败'}
            </small>
        </div>
        `}
    `;
    
    // 添加到输出容器
    outputContainer.appendChild(resultElement);
    
    // 滚动到最新结果
    outputContainer.scrollTop = outputContainer.scrollHeight;
}

/**
 * 获取结果样式类
 */
function getResultClass(resultData) {
    if (resultData.isError) return 'result-error';
    if (resultData.success) return 'result-success';
    return 'result-warning';
}

/**
 * 格式化命令结果显示
 */
function formatCommandResult(resultData) {
    if (resultData.isError) {
        return `❌ 错误信息: ${escapeHtml(resultData.message)}
🌐 错误详情: ${escapeHtml(resultData.error?.stack || resultData.error?.toString() || '未知错误')}`;
    }
    
    // 处理成功的响应
    let resultText = '';
    
    if (resultData.data && typeof resultData.data === 'object') {
        // 显示关键信息
        if (resultData.data['用户输入的命令']) {
            resultText += `📥 输入命令: ${escapeHtml(resultData.data['用户输入的命令'])}\n`;
        }
        
        if (resultData.data['后端执行的完整命令']) {
            resultText += `⚙️ 执行命令: ${escapeHtml(resultData.data['后端执行的完整命令'])}\n`;
        }
        
        if (resultData.data['命令执行结果']) {
            resultText += `\n📋 执行结果:\n${escapeHtml(resultData.data['命令执行结果'])}\n`;
        }
        
        if (resultData.data['漏洞本质']) {
            resultText += `\n⚠️ 漏洞说明: ${escapeHtml(resultData.data['漏洞本质'])}\n`;
        }
    } else {
        // 原始响应数据
        resultText = escapeHtml(JSON.stringify(resultData.rawResponse, null, 2));
    }
    
    return resultText || '命令执行完成，无输出结果';
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
 * 快捷命令执行
 */
function executeQuickCommand(command) {
    const commandInput = document.getElementById('commandInput');
    commandInput.value = command;
    commandInput.focus();
    setTimeout(() => executeCommand(), 100);
}

/**
 * 清空输出
 */
function clearOutput() {
    const outputContainer = document.getElementById('outputContainer');
    outputContainer.innerHTML = `
        <div class="welcome-message p-4 text-center text-muted">
            <i class="fas fa-terminal fa-3x mb-3"></i>
            <h4>输出已清空</h4>
            <p class="mb-0">请重新输入命令开始测试</p>
        </div>
    `;
    showNotification('输出已清空', 'info');
}

/**
 * 添加到命令历史记录
 */
function addToHistory(command) {
    // 避免重复添加
    if (commandHistory.length === 0 || commandHistory[commandHistory.length - 1] !== command) {
        commandHistory.push(command);
        // 限制历史记录数量
        if (commandHistory.length > 50) {
            commandHistory.shift();
        }
    }
    currentHistoryIndex = commandHistory.length;
}

/**
 * 处理历史记录导航（上下箭头键）
 */
function handleHistoryNavigation(e) {
    if (commandHistory.length === 0) return;
    
    const input = e.target;
    
    switch (e.key) {
        case 'ArrowUp':
            e.preventDefault();
            if (currentHistoryIndex > 0) {
                currentHistoryIndex--;
                input.value = commandHistory[currentHistoryIndex];
            }
            break;
        case 'ArrowDown':
            e.preventDefault();
            if (currentHistoryIndex < commandHistory.length - 1) {
                currentHistoryIndex++;
                input.value = commandHistory[currentHistoryIndex];
            } else {
                currentHistoryIndex = commandHistory.length;
                input.value = '';
            }
            break;
    }
}

/**
 * 更新状态指示器
 */
function updateStatus(status, text) {
    const statusIndicator = document.getElementById('statusIndicator');
    statusIndicator.className = `badge ${getStatusClass(status)}`;
    statusIndicator.innerHTML = getStatusIcon(status) + ' ' + text;
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
    if (show) {
        overlay.classList.add('show');
    } else {
        overlay.classList.remove('show');
    }
}

/**
 * 显示通知消息（改进版：确保定位正确）
 */
function showNotification(message, type = 'info') {
    // 先移除已存在的同类型通知（防重复）
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

    // 直接插入到 body 开头（确保在最上层）
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
 * 导出测试报告
 */
function exportTestReport() {
    const outputContainer = document.getElementById('outputContainer');
    const reportContent = `
CMD命令注入漏洞测试报告
========================

生成时间: ${new Date().toLocaleString('zh-CN')}
测试环境: ${document.getElementById('osInfo').textContent}

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
    a.download = `cmd-vuln-test-report-${new Date().getTime()}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    showNotification('测试报告已导出', 'success');
}

// 暴露全局函数供HTML调用
window.executeQuickCommand = executeQuickCommand;
window.clearOutput = clearOutput;
window.exportTestReport = exportTestReport;