/**
 * 리뷰 게시판 JavaScript
 * 
 * API Base URL: http://localhost:8185/wherehouse
 * Endpoints:
 * - GET /api/v1/reviews (목록 조회)
 * - POST /api/v1/reviews (작성)
 * - GET /api/v1/reviews/{id} (상세 조회)
 * - POST /api/v1/reviews/update (수정)
 * - DELETE /api/v1/reviews/{id} (삭제)
 */

// ========== 전역 변수 ==========
const BASE_URL = 'http://localhost:8185/wherehouse';
const API_URL = BASE_URL + '/api/v1/reviews';

let currentPage = 1;
let currentSize = 10;
let currentSort = 'latest';
let currentPropertyId = null;
let currentRatingFilter = null;

let currentReviewIdForDetail = null;
let currentReviewIdForDelete = null;

// ========== 초기화 ==========
window.onload = function() {
    console.log('리뷰 게시판 초기화 시작');
    
    // 초기 데이터 로드
    loadReviews();
    
    // 이벤트 리스너 등록
    initEventListeners();
    
    console.log('리뷰 게시판 초기화 완료');
};

// ========== 이벤트 리스너 초기화 ==========
function initEventListeners() {
    
    // 사이드바 토글
    document.getElementById('btn').addEventListener('click', toggleSidebar);
    
    // 필터 적용 버튼
    document.getElementById('apply_filter_btn').addEventListener('click', applyFilters);
    
    // 매물 ID 클리어 버튼
    document.getElementById('clear_property_id').addEventListener('click', clearPropertyIdSearch);
    
    // 리뷰 작성 버튼
    document.getElementById('write_review_btn').addEventListener('click', openWriteModal);
    
    // 리뷰 작성 폼 제출
    document.getElementById('review_form').addEventListener('submit', submitReview);
    
    // 리뷰 수정 폼 제출
    document.getElementById('review_edit_form').addEventListener('submit', submitEditReview);
    
    // 삭제 확인 버튼
    document.getElementById('confirm_delete_btn').addEventListener('click', confirmDeleteReview);
    
    // 리뷰 내용 글자 수 카운터
    document.getElementById('input_content').addEventListener('input', function() {
        updateCharCount('input_content', 'current_char_count');
    });
    
    document.getElementById('edit_content').addEventListener('input', function() {
        updateCharCount('edit_content', 'edit_char_count');
    });
}

// ========== API 호출 함수 ==========

/**
 * 리뷰 목록 조회
 */
function loadReviews() {
    console.log('리뷰 목록 조회 시작:', {
        page: currentPage,
        size: currentSize,
        sort: currentSort,
        propertyId: currentPropertyId,
        ratingFilter: currentRatingFilter
    });
    
    // Query Parameter 생성
    let params = new URLSearchParams({
        page: currentPage,
        size: currentSize,
        sort: currentSort
    });
    
    if (currentPropertyId && currentPropertyId.trim() !== '') {
        params.append('property_id', currentPropertyId.trim());
    }
    
    // 별점 필터는 클라이언트 측에서 처리 (API에서 지원하지 않음)
    
    const url = API_URL + '?' + params.toString();
    console.log('요청 URL:', url);
    
    fetch(url)
        .then(response => {
            console.log('응답 상태:', response.status);
            if (!response.ok) {
                throw new Error('리뷰 목록 조회 실패: ' + response.status);
            }
            return response.json();
        })
        .then(data => {
            console.log('응답 데이터:', data);
            
            // 별점 필터 적용 (클라이언트 측)
            let filteredReviews = data.reviews;
            if (currentRatingFilter) {
                filteredReviews = filteredReviews.filter(review => 
                    review.rating === parseInt(currentRatingFilter)
                );
            }
            
            // 렌더링
            renderReviews(filteredReviews);
            renderPagination(data.pagination);
            updateHeader(data.pagination, data.filter_meta);
            
            // 매물 통계 표시 (특정 매물 필터 시)
            if (data.filter_meta) {
                showPropertyStats(data.filter_meta);
            } else {
                hidePropertyStats();
            }
        })
        .catch(error => {
            console.error('리뷰 목록 조회 오류:', error);
            showError('리뷰 목록을 불러오는데 실패했습니다.');
        });
}

/**
 * 리뷰 작성
 */
function createReview(reviewData) {
    console.log('리뷰 작성 요청:', reviewData);
    
    fetch(API_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(reviewData)
    })
        .then(response => {
            console.log('응답 상태:', response.status);
            if (!response.ok) {
                return response.json().then(err => {
                    throw new Error(err.message || '리뷰 작성 실패');
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('리뷰 작성 성공:', data);
            closeWriteModal();
            showSuccess('리뷰가 성공적으로 작성되었습니다.');
            
            // 목록 새로고침
            currentPage = 1;
            loadReviews();
        })
        .catch(error => {
            console.error('리뷰 작성 오류:', error);
            showFormError('form_error_message', error.message);
        });
}

/**
 * 리뷰 상세 조회
 */
function loadReviewDetail(reviewId) {
    console.log('리뷰 상세 조회:', reviewId);
    
    fetch(API_URL + '/' + reviewId)
        .then(response => {
            console.log('응답 상태:', response.status);
            if (!response.ok) {
                throw new Error('리뷰 상세 조회 실패');
            }
            return response.json();
        })
        .then(data => {
            console.log('리뷰 상세 데이터:', data);
            showDetailModal(data);
        })
        .catch(error => {
            console.error('리뷰 상세 조회 오류:', error);
            showError('리뷰 상세 정보를 불러오는데 실패했습니다.');
        });
}

/**
 * 리뷰 수정
 */
function updateReview(reviewData) {
    console.log('리뷰 수정 요청:', reviewData);
    
    fetch(API_URL + '/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(reviewData)
    })
        .then(response => {
            console.log('응답 상태:', response.status);
            if (!response.ok) {
                return response.json().then(err => {
                    throw new Error(err.message || '리뷰 수정 실패');
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('리뷰 수정 성공:', data);
            closeEditModal();
            closeDetailModal();
            showSuccess('리뷰가 성공적으로 수정되었습니다.');
            
            // 목록 새로고침
            loadReviews();
        })
        .catch(error => {
            console.error('리뷰 수정 오류:', error);
            showFormError('edit_error_message', error.message);
        });
}

/**
 * 리뷰 삭제
 */
function deleteReview(reviewId) {
    console.log('리뷰 삭제 요청:', reviewId);
    
    fetch(API_URL + '/' + reviewId, {
        method: 'DELETE'
    })
        .then(response => {
            console.log('응답 상태:', response.status);
            if (!response.ok) {
                throw new Error('리뷰 삭제 실패');
            }
            console.log('리뷰 삭제 성공');
            closeDeleteConfirmModal();
            closeDetailModal();
            showSuccess('리뷰가 성공적으로 삭제되었습니다.');
            
            // 목록 새로고침
            loadReviews();
        })
        .catch(error => {
            console.error('리뷰 삭제 오류:', error);
            showError('리뷰 삭제에 실패했습니다.');
        });
}

// ========== 렌더링 함수 ==========

/**
 * 리뷰 목록 렌더링
 */
function renderReviews(reviews) {
    const container = document.getElementById('review_list_container');
    
    if (!reviews || reviews.length === 0) {
        container.innerHTML = `
            <div class="empty_state">
                <i class="fas fa-inbox"></i>
                <p>리뷰가 없습니다</p>
                <p class="empty_desc">첫 번째 리뷰를 작성해보세요!</p>
            </div>
        `;
        return;
    }
    
    container.innerHTML = reviews.map(review => createReviewCard(review)).join('');
    
    // 카드 클릭 이벤트 등록
    container.querySelectorAll('.review_card').forEach((card, index) => {
        card.addEventListener('click', () => {
            loadReviewDetail(reviews[index].review_id);
        });
    });
}

/**
 * 리뷰 카드 HTML 생성
 */
function createReviewCard(review) {
    const stars = '⭐'.repeat(review.rating);
    const tagsHtml = review.tags && review.tags.length > 0
        ? review.tags.slice(0, 5).map(tag => `<span class="tag">#${tag}</span>`).join('')
        : '';
    
    return `
        <div class="review_card" data-review-id="${review.review_id}">
            <div class="review_card_header">
                <div class="review_rating">${stars}</div>
                <div class="review_date">${formatDate(review.created_at)}</div>
            </div>
            
            <div class="review_user">
                <i class="fas fa-user"></i> ${review.user_id}
            </div>
            
            <div class="review_summary">
                ${escapeHtml(review.summary || '')}
            </div>
            
            <div class="review_tags">
                ${tagsHtml}
            </div>
            
            <div class="review_property_info">
                <i class="fas fa-home"></i> ${review.property_name || 'N/A'}
                <span style="margin-left: 10px; color: #ddd;">|</span>
                <span style="margin-left: 10px;">${review.property_id.substring(0, 8)}...</span>
            </div>
        </div>
    `;
}

/**
 * 페이지네이션 렌더링
 */
function renderPagination(pagination) {
    const container = document.getElementById('pagination_container');
    
    if (!pagination || pagination.total_pages === 0) {
        container.innerHTML = '';
        return;
    }
    
    let html = '';
    
    // 이전 버튼
    html += `
        <button class="pagination_btn" ${!pagination.has_previous ? 'disabled' : ''} 
                onclick="goToPage(${pagination.current_page - 1})">
            <i class="fas fa-chevron-left"></i>
        </button>
    `;
    
    // 페이지 번호 버튼
    const startPage = Math.max(1, pagination.current_page - 2);
    const endPage = Math.min(pagination.total_pages, pagination.current_page + 2);
    
    if (startPage > 1) {
        html += `<button class="pagination_btn" onclick="goToPage(1)">1</button>`;
        if (startPage > 2) {
            html += `<span class="pagination_info">...</span>`;
        }
    }
    
    for (let i = startPage; i <= endPage; i++) {
        const activeClass = i === pagination.current_page ? 'active' : '';
        html += `
            <button class="pagination_btn ${activeClass}" onclick="goToPage(${i})">
                ${i}
            </button>
        `;
    }
    
    if (endPage < pagination.total_pages) {
        if (endPage < pagination.total_pages - 1) {
            html += `<span class="pagination_info">...</span>`;
        }
        html += `
            <button class="pagination_btn" onclick="goToPage(${pagination.total_pages})">
                ${pagination.total_pages}
            </button>
        `;
    }
    
    // 다음 버튼
    html += `
        <button class="pagination_btn" ${!pagination.has_next ? 'disabled' : ''} 
                onclick="goToPage(${pagination.current_page + 1})">
            <i class="fas fa-chevron-right"></i>
        </button>
    `;
    
    // 페이지 정보
    html += `
        <span class="pagination_info">
            (${pagination.current_page} / ${pagination.total_pages})
        </span>
    `;
    
    container.innerHTML = html;
}

/**
 * 헤더 정보 업데이트
 */
function updateHeader(pagination, filterMeta) {
    const totalReviews = pagination ? pagination.total_items : 0;
    document.getElementById('total_reviews').textContent = totalReviews;
    
    const headerTitle = document.querySelector('#content_header h2');
    if (filterMeta) {
        headerTitle.textContent = '매물 리뷰';
    } else {
        headerTitle.textContent = '전체 리뷰';
    }
}

/**
 * 매물 통계 표시
 */
function showPropertyStats(filterMeta) {
    document.getElementById('property_stats_section').style.display = 'block';
    document.getElementById('filtered_property_id').textContent = 
        filterMeta.target_property_id.substring(0, 16) + '...';
    document.getElementById('property_avg_rating').textContent = 
        filterMeta.property_avg_rating.toFixed(1) + ' ⭐';
}

/**
 * 매물 통계 숨기기
 */
function hidePropertyStats() {
    document.getElementById('property_stats_section').style.display = 'none';
}

// ========== 모달 관련 함수 ==========

/**
 * 리뷰 작성 모달 열기
 */
function openWriteModal() {
    document.getElementById('write_review_modal').style.display = 'block';
    document.getElementById('review_form').reset();
    hideFormError('form_error_message');
    updateCharCount('input_content', 'current_char_count');
}

/**
 * 리뷰 작성 모달 닫기
 */
function closeWriteModal() {
    document.getElementById('write_review_modal').style.display = 'none';
}

/**
 * 리뷰 상세 모달 표시
 */
function showDetailModal(reviewData) {
    currentReviewIdForDetail = reviewData.review_id;
    
    const stars = '⭐'.repeat(reviewData.rating);
    document.getElementById('detail_rating').textContent = stars;
    document.getElementById('detail_created_at').textContent = formatDate(reviewData.created_at);
    document.getElementById('detail_user_id').textContent = reviewData.user_id;
    document.getElementById('detail_property_id').textContent = reviewData.property_id;
    
    // 태그 렌더링
    const tagsContainer = document.getElementById('detail_tags');
    if (reviewData.tags && reviewData.tags.length > 0) {
        tagsContainer.innerHTML = reviewData.tags
            .map(tag => `<span class="tag">#${tag}</span>`)
            .join('');
    } else {
        tagsContainer.innerHTML = '<span style="color: #999; font-size: 12px;">태그 없음</span>';
    }
    
    // 내용 렌더링
    document.getElementById('detail_content').textContent = reviewData.content;
    
    // 수정 일시 표시
    if (reviewData.updated_at) {
        document.getElementById('detail_updated_at').style.display = 'block';
        document.getElementById('detail_updated_time').textContent = formatDate(reviewData.updated_at);
    } else {
        document.getElementById('detail_updated_at').style.display = 'none';
    }
    
    // 수정/삭제 버튼 이벤트
    document.getElementById('btn_edit_review').onclick = () => openEditModal(reviewData);
    document.getElementById('btn_delete_review').onclick = () => openDeleteConfirmModal(reviewData.review_id);
    
    document.getElementById('detail_review_modal').style.display = 'block';
}

/**
 * 리뷰 상세 모달 닫기
 */
function closeDetailModal() {
    document.getElementById('detail_review_modal').style.display = 'none';
    currentReviewIdForDetail = null;
}

/**
 * 리뷰 수정 모달 열기
 */
function openEditModal(reviewData) {
    document.getElementById('edit_review_id').value = reviewData.review_id;
    document.getElementById('edit_rating').value = reviewData.rating;
    document.getElementById('edit_content').value = reviewData.content;
    updateCharCount('edit_content', 'edit_char_count');
    hideFormError('edit_error_message');
    
    document.getElementById('edit_review_modal').style.display = 'block';
}

/**
 * 리뷰 수정 모달 닫기
 */
function closeEditModal() {
    document.getElementById('edit_review_modal').style.display = 'none';
}

/**
 * 삭제 확인 모달 열기
 */
function openDeleteConfirmModal(reviewId) {
    currentReviewIdForDelete = reviewId;
    document.getElementById('delete_confirm_modal').style.display = 'block';
}

/**
 * 삭제 확인 모달 닫기
 */
function closeDeleteConfirmModal() {
    document.getElementById('delete_confirm_modal').style.display = 'none';
    currentReviewIdForDelete = null;
}

// ========== 폼 제출 핸들러 ==========

/**
 * 리뷰 작성 폼 제출
 */
function submitReview(event) {
    event.preventDefault();
    
    const propertyId = document.getElementById('input_property_id').value.trim();
    const userId = document.getElementById('input_user_id').value.trim();
    const rating = parseInt(document.getElementById('input_rating').value);
    const content = document.getElementById('input_content').value.trim();
    
    // 유효성 검사
    if (propertyId.length !== 32) {
        showFormError('form_error_message', '매물 ID는 32자여야 합니다.');
        return;
    }
    
    if (!userId || userId.length > 50) {
        showFormError('form_error_message', '사용자 ID는 1~50자여야 합니다.');
        return;
    }
    
    if (!rating || rating < 1 || rating > 5) {
        showFormError('form_error_message', '별점을 선택해주세요.');
        return;
    }
    
    if (content.length < 20 || content.length > 1000) {
        showFormError('form_error_message', '리뷰 내용은 20자 이상 1000자 이하여야 합니다.');
        return;
    }
    
    // API 요청 데이터 (snake_case)
    const reviewData = {
        property_id: propertyId,
        user_id: userId,
        rating: rating,
        content: content
    };
    
    createReview(reviewData);
}

/**
 * 리뷰 수정 폼 제출
 */
function submitEditReview(event) {
    event.preventDefault();
    
    const reviewId = parseInt(document.getElementById('edit_review_id').value);
    const rating = parseInt(document.getElementById('edit_rating').value);
    const content = document.getElementById('edit_content').value.trim();
    
    // 유효성 검사
    if (!rating || rating < 1 || rating > 5) {
        showFormError('edit_error_message', '별점을 선택해주세요.');
        return;
    }
    
    if (content.length < 20 || content.length > 1000) {
        showFormError('edit_error_message', '리뷰 내용은 20자 이상 1000자 이하여야 합니다.');
        return;
    }
    
    // API 요청 데이터 (snake_case)
    const reviewData = {
        review_id: reviewId,
        rating: rating,
        content: content
    };
    
    updateReview(reviewData);
}

/**
 * 삭제 확인
 */
function confirmDeleteReview() {
    if (currentReviewIdForDelete) {
        deleteReview(currentReviewIdForDelete);
    }
}

// ========== 필터 관련 함수 ==========

/**
 * 필터 적용
 */
function applyFilters() {
    currentSort = document.getElementById('sort_select').value;
    currentRatingFilter = document.getElementById('rating_filter').value;
    currentPropertyId = document.getElementById('property_id_search').value.trim();
    
    // 매물 ID 유효성 검사
    if (currentPropertyId && currentPropertyId.length !== 32) {
        showError('매물 ID는 32자여야 합니다.');
        return;
    }
    
    currentPage = 1;
    loadReviews();
}

/**
 * 매물 ID 검색 클리어
 */
function clearPropertyIdSearch() {
    document.getElementById('property_id_search').value = '';
    currentPropertyId = null;
    currentPage = 1;
    loadReviews();
}

/**
 * 페이지 이동
 */
function goToPage(page) {
    currentPage = page;
    loadReviews();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ========== 사이드바 토글 ==========

function toggleSidebar() {
    const information = document.getElementById('information');
    const btn = document.getElementById('btn');
    const mainContent = document.getElementById('main_content');
    
    if (information.style.left === '-480px') {
        information.style.left = '0';
        btn.style.left = '475px';
        btn.innerHTML = '▼';
        mainContent.style.marginLeft = '480px';
    } else {
        information.style.left = '-480px';
        btn.style.left = '10px';
        btn.innerHTML = '▶';
        mainContent.style.marginLeft = '0';
    }
}

// ========== 유틸리티 함수 ==========

/**
 * 날짜 포맷팅
 */
function formatDate(dateString) {
    if (!dateString) return '-';
    
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;
    
    // 1분 미만
    if (diff < 60000) {
        return '방금 전';
    }
    
    // 1시간 미만
    if (diff < 3600000) {
        const minutes = Math.floor(diff / 60000);
        return minutes + '분 전';
    }
    
    // 24시간 미만
    if (diff < 86400000) {
        const hours = Math.floor(diff / 3600000);
        return hours + '시간 전';
    }
    
    // 7일 미만
    if (diff < 604800000) {
        const days = Math.floor(diff / 86400000);
        return days + '일 전';
    }
    
    // 그 외: YYYY-MM-DD HH:mm 형식
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    
    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

/**
 * HTML 이스케이프
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * 글자 수 카운터 업데이트
 */
function updateCharCount(textareaId, counterId) {
    const textarea = document.getElementById(textareaId);
    const counter = document.getElementById(counterId);
    counter.textContent = textarea.value.length;
}

/**
 * 폼 에러 메시지 표시
 */
function showFormError(elementId, message) {
    const errorElement = document.getElementById(elementId);
    errorElement.textContent = message;
    errorElement.classList.add('show');
}

/**
 * 폼 에러 메시지 숨기기
 */
function hideFormError(elementId) {
    const errorElement = document.getElementById(elementId);
    errorElement.textContent = '';
    errorElement.classList.remove('show');
}

/**
 * 성공 메시지 표시 (간단한 alert)
 */
function showSuccess(message) {
    alert(message);
}

/**
 * 에러 메시지 표시 (간단한 alert)
 */
function showError(message) {
    alert('오류: ' + message);
}
