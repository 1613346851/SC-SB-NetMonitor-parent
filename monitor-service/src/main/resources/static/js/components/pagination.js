/**
 * 网络监测系统 - 分页组件
 * 提供统一的分页功能
 */

const PaginationUtil = {
    create(options = {}) {
        const {
            container,
            total = 0,
            pageSize = AppConfig.PAGINATION.DEFAULT_PAGE_SIZE,
            currentPage = 1,
            onChange = () => {}
        } = options;
        
        const state = {
            total,
            pageSize,
            currentPage,
            onChange
        };
        
        const render = () => {
            const totalPages = Math.ceil(state.total / state.pageSize);
            
            if (totalPages <= 1) {
                container.innerHTML = '';
                return;
            }
            
            let html = '<div class="pagination">';
            
            html += `<span class="pagination-item ${state.currentPage === 1 ? 'disabled' : ''}" data-page="${state.currentPage - 1}">上一页</span>`;
            
            const pages = [];
            if (totalPages <= 7) {
                for (let i = 1; i <= totalPages; i++) pages.push(i);
            } else {
                if (state.currentPage <= 3) {
                    pages.push(1, 2, 3, 4, '...', totalPages);
                } else if (state.currentPage >= totalPages - 2) {
                    pages.push(1, '...', totalPages - 3, totalPages - 2, totalPages - 1, totalPages);
                } else {
                    pages.push(1, '...', state.currentPage - 1, state.currentPage, state.currentPage + 1, '...', totalPages);
                }
            }
            
            pages.forEach(page => {
                if (page === '...') {
                    html += '<span class="pagination-item">...</span>';
                } else {
                    html += `<span class="pagination-item ${page === state.currentPage ? 'active' : ''}" data-page="${page}">${page}</span>`;
                }
            });
            
            html += `<span class="pagination-item ${state.currentPage === totalPages ? 'disabled' : ''}" data-page="${state.currentPage + 1}">下一页</span>`;
            html += `<span class="pagination-item">共 ${state.total} 条</span>`;
            html += '</div>';
            
            container.innerHTML = html;
            
            container.querySelectorAll('.pagination-item[data-page]').forEach(el => {
                el.addEventListener('click', () => {
                    const page = parseInt(el.dataset.page);
                    if (!isNaN(page) && page >= 1 && page <= totalPages && page !== state.currentPage) {
                        state.currentPage = page;
                        render();
                        state.onChange(page, state.pageSize);
                    }
                });
            });
        };
        
        const api = {
            render,
            setPage(page) {
                state.currentPage = page;
                render();
            },
            setTotal(total) {
                state.total = total;
                render();
            },
            setPageSize(size) {
                state.pageSize = size;
                state.currentPage = 1;
                render();
            },
            getState() {
                return { ...state };
            }
        };
        
        render();
        
        return api;
    }
};

window.PaginationUtil = PaginationUtil;
