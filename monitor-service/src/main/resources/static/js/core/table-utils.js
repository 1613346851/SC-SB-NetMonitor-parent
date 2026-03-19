/**
 * 通用表格工具类
 * 封装分页、搜索、排序、导出等通用功能
 */

const TableUtils = {
    createInstance(options) {
        return new TableInstance(options);
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
            ...options
        };
        
        this.currentPage = 1;
        this.totalCount = 0;
        this.searchParams = {};
        this.sortField = this.options.defaultSortField;
        this.sortOrder = this.options.defaultSortOrder;
        this.data = [];
    }

    async loadData() {
        try {
            const params = {
                pageNum: this.currentPage,
                pageSize: this.options.pageSize,
                sortField: this.sortField,
                sortOrder: this.sortOrder,
                ...this.searchParams
            };

            const result = await http.get(this.options.apiUrl, params);
            
            this.data = result.list || [];
            this.totalCount = result.total || 0;
            
            this.renderTable();
            this.renderPagination();
            
            return result;
        } catch (error) {
            console.error('加载数据失败:', error);
            this.renderEmpty();
            throw error;
        }
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
        
        const colspan = this.options.colspan || 10;
        tbody.innerHTML = `<tr><td colspan="${colspan}" class="text-center">暂无数据</td></tr>`;
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
        this.loadData();
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
}

window.TableUtils = TableUtils;
window.TableInstance = TableInstance;
