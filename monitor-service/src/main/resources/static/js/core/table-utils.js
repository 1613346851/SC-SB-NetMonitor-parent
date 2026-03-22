/**
 * 通用表格工具类
 * 封装分页、搜索、排序、导出、悬浮提示等通用功能
 */

(function() {
    let cellTooltip = null;
    let tooltipInitialized = false;

    const TooltipManager = {
        init() {
            if (tooltipInitialized) return;
            
            cellTooltip = document.createElement('div');
            cellTooltip.className = 'cell-tooltip';
            document.body.appendChild(cellTooltip);
            tooltipInitialized = true;
        },
        
        show(text, e) {
            if (!cellTooltip) this.init();
            if (!text || text.length === 0) return;
            
            cellTooltip.textContent = text;
            cellTooltip.classList.add('visible');
            this.updatePosition(e);
        },
        
        hide() {
            if (cellTooltip) {
                cellTooltip.classList.remove('visible');
            }
        },
        
        updatePosition(e) {
            if (!cellTooltip) return;
            
            const padding = 10;
            let x = e.clientX + padding;
            let y = e.clientY + padding;
            
            const tooltipRect = cellTooltip.getBoundingClientRect();
            const viewportWidth = window.innerWidth;
            const viewportHeight = window.innerHeight;
            
            if (x + tooltipRect.width > viewportWidth - padding) {
                x = viewportWidth - tooltipRect.width - padding;
            }
            if (y + tooltipRect.height > viewportHeight - padding) {
                y = e.clientY - tooltipRect.height - padding;
            }
            
            cellTooltip.style.left = x + 'px';
            cellTooltip.style.top = y + 'px';
        },
        
        bindEvents(tbodyEl) {
            const tbody = typeof tbodyEl === 'string' 
                ? document.getElementById(tbodyEl) 
                : tbodyEl;
            
            if (!tbody) return;
            
            tbody.addEventListener('mouseover', (e) => {
                const cell = e.target.closest('td.cell-overflow');
                if (cell) {
                    const fullText = cell.dataset.fullText;
                    if (fullText) {
                        this.show(fullText, e);
                    }
                }
            });
            
            tbody.addEventListener('mouseout', (e) => {
                const cell = e.target.closest('td.cell-overflow');
                if (cell) {
                    this.hide();
                }
            });
            
            tbody.addEventListener('mousemove', (e) => {
                const cell = e.target.closest('td.cell-overflow');
                if (cell && cellTooltip && cellTooltip.classList.contains('visible')) {
                    this.updatePosition(e);
                }
            });
        }
    };

    const CellRenderer = {
        escapeAttr(str) {
            if (!str) return '';
            return String(str)
                .replace(/\\/g, '\\\\')
                .replace(/&/g, '&amp;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
        },
        
        escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        },
        
        renderText(text, maxLength = 0) {
            if (!text) return '-';
            const escaped = this.escapeHtml(text);
            if (maxLength > 0 && escaped.length > maxLength) {
                return escaped.substring(0, maxLength) + '...';
            }
            return escaped;
        },
        
        renderCell(text, options = {}) {
            const {
                maxLength = 60,
                showTooltip = true,
                className = ''
            } = options;
            
            const displayText = this.renderText(text, maxLength);
            
            if (showTooltip && text && text.length > maxLength) {
                const escapedFull = this.escapeAttr(text);
                return `<td class="cell-overflow ${className}" data-full-text="${escapedFull}">${displayText}</td>`;
            }
            
            return `<td class="${className}">${displayText}</td>`;
        },
        
        renderRiskLevel(level) {
            const levelMap = {
                'CRITICAL': { text: '严重', class: 'critical' },
                'HIGH': { text: '高风险', class: 'high' },
                'MEDIUM': { text: '中风险', class: 'medium' },
                'LOW': { text: '低风险', class: 'low' },
                '严重': { text: '严重', class: 'critical' },
                '高': { text: '高风险', class: 'high' },
                '中': { text: '中风险', class: 'medium' },
                '低': { text: '低风险', class: 'low' }
            };
            
            const info = levelMap[level] || { text: level || '-', class: '' };
            return `<span class="risk-tag ${info.class}">${info.text}</span>`;
        },
        
        renderStatus(enabled, trueText = '启用', falseText = '禁用') {
            if (enabled === 1 || enabled === true || enabled === '1') {
                return `<span class="tag success">${trueText}</span>`;
            }
            return `<span class="tag info">${falseText}</span>`;
        },
        
        renderAttackType(type) {
            if (!type) return '-';
            return `<span class="attack-type-tag">${this.escapeHtml(type)}</span>`;
        },
        
        renderActionCell(buttons, options = {}) {
            const { width = '200px' } = options;
            return `<td class="action-cell" style="min-width: ${width}; max-width: ${width}; width: ${width};">
                <div class="action-btns-fixed">${buttons}</div>
            </td>`;
        },
        
        renderButton(text, type, onclick, options = {}) {
            const { size = 'sm', className = '' } = options;
            const btnClass = `btn btn-${type} btn-${size} ${className}`.trim();
            return `<button class="${btnClass}" onclick="${onclick}">${text}</button>`;
        },
        
        renderToggleButton(currentValue, id, trueText = '禁用', falseText = '启用', callback = 'toggleStatus') {
            if (currentValue === 1 || currentValue === true || currentValue === '1') {
                return this.renderButton(trueText, 'warning', `${callback}(${id}, ${currentValue})`);
            }
            return this.renderButton(falseText, 'success', `${callback}(${id}, ${currentValue})`);
        }
    };

    const TableUtils = {
        createInstance(options) {
            return new TableInstance(options);
        },
        
        initTooltip() {
            TooltipManager.init();
        },
        
        bindTooltip(tbodyEl) {
            TooltipManager.bindEvents(tbodyEl);
        },
        
        cell: CellRenderer,
        
        renderHeader(columns, options = {}) {
            const { sortField = null, sortOrder = 'desc', fixedAction = false, actionWidth = '200px' } = options;
            
            let html = columns.map((col, index) => {
                const {
                    field,
                    title,
                    sortable = false,
                    width = null,
                    minWidth = null,
                    align = 'left',
                    className = '',
                    isAction = false
                } = col;
                
                let classes = [];
                if (sortable) classes.push('sortable');
                if (className) classes.push(className);
                if (isAction) classes.push('action-header');
                
                let attrs = [];
                if (sortable && field) attrs.push(`data-sort="${field}"`);
                
                let styleAttrs = [];
                if (width) {
                    styleAttrs.push(`width: ${width}`);
                    styleAttrs.push(`min-width: ${width}`);
                } else if (minWidth) {
                    styleAttrs.push(`min-width: ${minWidth}`);
                }
                if (isAction && fixedAction) {
                    styleAttrs.push(`min-width: ${actionWidth}`);
                    styleAttrs.push(`max-width: ${actionWidth}`);
                    styleAttrs.push(`width: ${actionWidth}`);
                }
                if (styleAttrs.length > 0) {
                    attrs.push(`style="${styleAttrs.join('; ')}"`);
                }
                attrs.push(`class="${classes.join(' ')}"`);
                
                let sortIcon = '';
                if (sortable) {
                    let iconClass = 'sort-icon';
                    if (field === sortField) {
                        iconClass += ` ${sortOrder}`;
                    }
                    sortIcon = `<span class="${iconClass}"><span class="up">▲</span><span class="down">▼</span></span>`;
                }
                
                return `<th ${attrs.join(' ')}>${title}${sortIcon}<div class="th-resizer"></div></th>`;
            }).join('\n');
            
            return html;
        },
        
        initTableEnhancements(tableSelector, options = {}) {
            const tables = typeof tableSelector === 'string' 
                ? document.querySelectorAll(tableSelector) 
                : [tableSelector];
            
            tables.forEach(table => {
                this._initSortableHeaders(table, options);
                this._initResizableColumns(table);
            });
        },
        
        _initSortableHeaders(table, options = {}) {
            const {
                sortField = null,
                sortOrder = 'desc',
                onSort = null
            } = options;
            
            const ths = table.querySelectorAll('th[data-sort]');
            ths.forEach(th => {
                th.classList.add('sortable');
                if (!th.querySelector('.sort-icon')) {
                    th.innerHTML += `<span class="sort-icon"><span class="up">▲</span><span class="down">▼</span></span>`;
                }
            });
            
            if (sortField) {
                this._updateSortIcon(table, sortField, sortOrder);
            }
        },
        
        _updateSortIcon(table, sortField, sortOrder) {
            const ths = table.querySelectorAll('th[data-sort]');
            ths.forEach(th => {
                const icon = th.querySelector('.sort-icon');
                if (icon) {
                    icon.className = 'sort-icon';
                    if (th.dataset.sort === sortField) {
                        icon.classList.add(sortOrder);
                    }
                }
            });
        },
        
        _initResizableColumns(table) {
            const cols = table.querySelectorAll('th');
            cols.forEach(th => {
                if (th.querySelector('.th-resizer')) return;
                const resizer = document.createElement('div');
                resizer.classList.add('th-resizer');
                th.appendChild(resizer);
                
                let startX, startWidth;
                
                const onMouseMove = (e) => {
                    const newWidth = startWidth + (e.pageX - startX);
                    if (newWidth >= 50) {
                        th.style.width = `${newWidth}px`;
                        th.style.minWidth = `${newWidth}px`;
                    }
                };
                
                const onMouseUp = () => {
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);
                    resizer.classList.remove('th-resizing');
                };
                
                resizer.addEventListener('mousedown', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    startX = e.pageX;
                    startWidth = th.offsetWidth;
                    resizer.classList.add('th-resizing');
                    document.addEventListener('mousemove', onMouseMove);
                    document.addEventListener('mouseup', onMouseUp);
                });
            });
        },
        
        renderEmpty(colspan, message = '暂无数据') {
            return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px; color: #999;">${message}</td></tr>`;
        },
        
        renderLoading(colspan) {
            return `<tr><td colspan="${colspan}" class="text-center" style="padding: 40px;">
                <div class="spinner" style="width: 32px; height: 32px; border: 3px solid #e8e8e8; border-top-color: #4f46e5; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto;"></div>
                <div style="margin-top: 12px; color: #999;">加载中...</div>
            </td></tr>`;
        }
    };

    class TableInstance {
        constructor(options) {
            this.options = {
                apiUrl: '',
                pageSize: 10,
                defaultSortField: 'id',
                defaultSortOrder: 'desc',
                searchFields: [],
                renderRow: null,
                paginationEl: 'pagination',
                tableBodyEl: '',
                tableEl: '',
                columns: null,
                colspan: 10,
                fixedAction: false,
                actionWidth: '200px',
                enableTooltip: true,
                ...options
            };
            
            this.currentPage = 1;
            this.totalCount = 0;
            this.searchParams = {};
            this.sortField = this.options.defaultSortField;
            this.sortOrder = this.options.defaultSortOrder;
            this.data = [];
            this.initialized = false;
        }

        async loadData() {
            try {
                this.showLoading();
                
                const params = {
                    pageNum: this.currentPage,
                    pageSize: this.options.pageSize,
                    sortField: this.sortField,
                    sortOrder: this.sortOrder,
                    ...this.searchParams
                };

                const result = await http.get(this.options.apiUrl, params);
                
                if (result && typeof result === 'object') {
                    this.data = result.list || [];
                    this.totalCount = result.total || 0;
                } else if (Array.isArray(result)) {
                    this.data = result;
                    this.totalCount = result.length;
                } else {
                    this.data = [];
                    this.totalCount = 0;
                }
                
                this.renderTable();
                this.renderPagination();
                this.initEnhancements();
                
                return result;
            } catch (error) {
                console.error('加载数据失败:', error);
                this.data = [];
                this.totalCount = 0;
                this.renderEmpty();
                this.renderPagination();
                throw error;
            }
        }

        showLoading() {
            const tbody = document.getElementById(this.options.tableBodyEl);
            if (!tbody) return;
            tbody.innerHTML = TableUtils.renderLoading(this.options.colspan);
        }

        renderTable() {
            const tbody = document.getElementById(this.options.tableBodyEl);
            if (!tbody) return;

            if (!this.data || this.data.length === 0) {
                this.renderEmpty();
                return;
            }

            if (this.options.renderRow) {
                tbody.innerHTML = this.data.map(item => this.options.renderRow(item)).join('');
            }
        }

        renderEmpty() {
            const tbody = document.getElementById(this.options.tableBodyEl);
            if (!tbody) return;
            
            tbody.innerHTML = TableUtils.renderEmpty(this.options.colspan);
        }

        renderPagination() {
            const totalPages = Math.ceil(this.totalCount / this.options.pageSize);
            const paginationEl = document.getElementById(this.options.paginationEl);
            
            if (!paginationEl) return;

            const instanceName = this.options.instanceName;
            
            let html = `
                <span class="pagination-item ${this.currentPage === 1 ? 'disabled' : ''}" 
                      data-page="${this.currentPage - 1}" data-instance="${instanceName}">上一页</span>
            `;
            
            if (totalPages > 0) {
                for (let i = 1; i <= totalPages; i++) {
                    if (i === 1 || i === totalPages || (i >= this.currentPage - 1 && i <= this.currentPage + 1)) {
                        html += `
                            <span class="pagination-item ${i === this.currentPage ? 'active' : ''}" 
                                  data-page="${i}" data-instance="${instanceName}">${i}</span>
                        `;
                    } else if (i === this.currentPage - 2 || i === this.currentPage + 2) {
                        html += `<span class="pagination-item">...</span>`;
                    }
                }
            }
            
            html += `
                <span class="pagination-item ${this.currentPage >= totalPages || totalPages === 0 ? 'disabled' : ''}" 
                      data-page="${this.currentPage + 1}" data-instance="${instanceName}">下一页</span>
                <span class="pagination-item">共 ${this.totalCount} 条</span>
            `;
            
            paginationEl.innerHTML = html;
            
            paginationEl.querySelectorAll('.pagination-item[data-page]').forEach(el => {
                el.addEventListener('click', (e) => {
                    const page = parseInt(el.dataset.page);
                    const instance = window[el.dataset.instance];
                    if (instance && typeof instance.goPage === 'function') {
                        instance.goPage(page);
                    }
                });
            });
        }

        goPage(page) {
            const totalPages = Math.ceil(this.totalCount / this.options.pageSize);
            
            if (page < 1 || (totalPages > 0 && page > totalPages) || page === this.currentPage) {
                return;
            }
            
            this.currentPage = page;
            this.loadData();
        }

        search(params) {
            this.searchParams = { ...params };
            this.currentPage = 1;
            this.loadData();
        }

        resetSearch() {
            this.searchParams = {};
            this.currentPage = 1;
            this.loadData();
        }

        sort(field) {
            if (this.sortField === field) {
                this.sortOrder = this.sortOrder === 'asc' ? 'desc' : 'asc';
            } else {
                this.sortField = field;
                this.sortOrder = 'desc';
            }
            this.updateSortIcons();
            this.loadData();
        }

        updateSortIcons() {
            const tbody = document.getElementById(this.options.tableBodyEl);
            if (!tbody) return;
            const table = tbody.closest('table');
            if (!table) return;
            const ths = table.querySelectorAll('th[data-sort]');
            ths.forEach(th => {
                const icon = th.querySelector('.sort-icon');
                if (icon) {
                    icon.className = 'sort-icon';
                    if (th.dataset.sort === this.sortField) {
                        icon.classList.add(this.sortOrder);
                    }
                }
            });
        }

        initEnhancements() {
            const tbody = document.getElementById(this.options.tableBodyEl);
            if (!tbody) return;
            const table = tbody.closest('table');
            if (!table) return;
            
            if (this.options.fixedAction) {
                table.classList.add('has-fixed-action');
            }
            
            const ths = table.querySelectorAll('th[data-sort]');
            ths.forEach(th => {
                th.classList.add('sortable');
                if (!th.querySelector('.sort-icon')) {
                    th.innerHTML += `<span class="sort-icon"><span class="up">▲</span><span class="down">▼</span></span>`;
                }
                if (!th.dataset.sortBound) {
                    th.dataset.sortBound = 'true';
                    th.addEventListener('click', (e) => {
                        if (e.target.classList.contains('th-resizer')) return;
                        this.sort(th.dataset.sort);
                    });
                }
            });
            this.updateSortIcons();

            const cols = table.querySelectorAll('th');
            cols.forEach(th => {
                if (th.querySelector('.th-resizer')) return;
                const resizer = document.createElement('div');
                resizer.classList.add('th-resizer');
                th.appendChild(resizer);
                
                let startX, startWidth;
                
                const onMouseMove = (e) => {
                    const newWidth = startWidth + (e.pageX - startX);
                    th.style.width = `${newWidth}px`;
                    th.style.minWidth = `${newWidth}px`;
                };
                
                const onMouseUp = () => {
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);
                    resizer.classList.remove('th-resizing');
                };
                
                resizer.addEventListener('mousedown', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    startX = e.pageX;
                    startWidth = th.offsetWidth;
                    resizer.classList.add('th-resizing');
                    document.addEventListener('mousemove', onMouseMove);
                    document.addEventListener('mouseup', onMouseUp);
                });
            });

            if (this.options.enableTooltip) {
                TooltipManager.bindEvents(tbody);
            }
            
            if (!this.initialized) {
                TooltipManager.init();
                this.initialized = true;
            }
        }

        getSortIcon(field) {
            if (this.sortField !== field) return '↕';
            return this.sortOrder === 'asc' ? '↑' : '↓';
        }

        async exportCSV(exportUrl, filename = 'export.csv') {
            try {
                const token = AuthService.getToken();
                const params = new URLSearchParams({
                    ...this.searchParams,
                    sortField: this.sortField,
                    sortOrder: this.sortOrder
                });
                
                const response = await fetch(`${AppConfig.API_BASE_URL}${exportUrl}?${params.toString()}`, {
                    method: 'GET',
                    headers: {
                        [AppConfig.AUTH.TOKEN_HEADER]: `${AppConfig.AUTH.TOKEN_PREFIX}${token}`
                    }
                });
                
                if (!response.ok) {
                    if (response.status === 401) {
                        message.error('登录已过期，请重新登录');
                        AuthService.handleUnauthorized();
                        return;
                    }
                    throw new Error('导出失败');
                }
                
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                
                message.success('导出成功');
            } catch (error) {
                console.error('导出失败:', error);
                message.error('导出失败: ' + error.message);
            }
        }

        getSearchValue(fieldId) {
            const el = document.getElementById(fieldId);
            return el ? el.value.trim() : '';
        }

        getSearchSelectValue(fieldId) {
            const el = document.getElementById(fieldId);
            return el ? el.value : '';
        }

        getDateRangeValue(startId, endId) {
            const startDate = this.getSearchValue(startId);
            const endDate = this.getSearchValue(endId);
            
            return {
                startTime: startDate ? startDate + ' 00:00:00' : '',
                endTime: endDate ? endDate + ' 23:59:59' : ''
            };
        }

        refresh() {
            this.loadData();
        }

        getData() {
            return this.data;
        }

        getTotalCount() {
            return this.totalCount;
        }

        getCurrentPage() {
            return this.currentPage;
        }
    }

    window.TableUtils = TableUtils;
    window.TableInstance = TableInstance;
    window.CellRenderer = CellRenderer;
})();
