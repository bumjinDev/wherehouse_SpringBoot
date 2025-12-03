/**
 * 리뷰 게시판 JavaScript (결과 없음 알림 추가 버전)
 *
 * [수정 사항]
 * 1. load_reviews(): 검색 결과가 없을 경우 alert() 실행 로직 추가
 * 2. 기존 기능(매물 이름 검색, snake_case 처리, 상세 조회 등) 모두 유지
 */

// ========== 전역 변수 ==========
const BASE_URL = 'http://localhost:8185/wherehouse';
const API_URL = BASE_URL + '/api/v1/reviews';

let current_page = 1;
let current_sort = 'rating_desc';
let current_keyword = null;

let current_review_id_for_delete = null;
let current_review_id_for_detail = null;

// ========== 초기화 ==========
window.onload = function() {
    console.log('[Init] 리뷰 게시판 초기화 시작');

    // 초기 데이터 로드
    load_reviews();

    // 이벤트 리스너 등록
    init_event_listeners();

    console.log('[Init] 리뷰 게시판 초기화 완료');
};

// ========== 이벤트 리스너 초기화 ==========
function init_event_listeners() {

    // 사이드바 토글
    document.getElementById('btn').addEventListener('click', toggle_sidebar);

    // 필터 적용 버튼
    document.getElementById('apply_filter_btn').addEventListener('click', apply_filters);

    // 검색어 클리어 버튼
    document.getElementById('clear_keyword').addEventListener('click', clear_keyword_search);

    // 리뷰 작성 버튼
    document.getElementById('write_review_btn').addEventListener('click', open_write_modal);

    // 리뷰 작성 폼 제출
    document.getElementById('review_form').addEventListener('submit', submit_review);

    // 리뷰 수정 폼 제출
    document.getElementById('review_edit_form').addEventListener('submit', submit_edit_review);

    // 삭제 확인 버튼
    document.getElementById('confirm_delete_btn').addEventListener('click', confirm_delete_review);

    // 리뷰 내용 글자 수 카운터 (작성)
    const input_content = document.getElementById('input_content');
    if (input_content) {
        input_content.addEventListener('input', function() {
            update_char_count('input_content', 'current_char_count');
        });
    }

    // 리뷰 내용 글자 수 카운터 (수정)
    const edit_content = document.getElementById('edit_content');
    if (edit_content) {
        edit_content.addEventListener('input', function() {
            update_char_count('edit_content', 'edit_char_count');
        });
    }
}

// ========== API 호출 함수 ==========

/**
 * 리뷰 목록 조회
 * Method: GET
 * Path: /api/v1/reviews/list
 * Params: Query String
 */
function load_reviews() {
    // 1. UI 입력값 가져오기
    const prop_name_input = document.getElementById('filter_property_name');
    const prop_name_val = prop_name_input ? prop_name_input.value.trim() : null;

    console.log('[API Request] 리뷰 목록 조회 시작:', {
        page: current_page,
        sort: current_sort,
        keyword: current_keyword,
        propertyName: prop_name_val
    });

    // 2. 쿼리 파라미터 생성
    const params = new URLSearchParams({
        page: current_page,
        sort: current_sort
    });

    if (prop_name_val && prop_name_val !== '') {
        params.append('propertyName', prop_name_val);
    }

    if (current_keyword && current_keyword.trim() !== '') {
        params.append('keyword', current_keyword.trim());
    }

    // 3. GET 요청 전송
    fetch(`${API_URL}/list?${params.toString()}`, {
        method: 'GET'
    })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => {
                    throw new Error('리뷰 목록 조회 실패: ' + response.status + ' ' + text);
                });
            }
            return response.json();
        })
        .then(data => {
            console.warn('[API Response] 서버 응답 원본 데이터:', data);

            // [수정됨] 검색 결과 없음 알림 로직 추가
            if (!data.reviews || data.reviews.length === 0) {
                alert('검색 결과가 없습니다.');
            } else {
                // 데이터가 있을 때만 샘플 검증 로그 출력
                const sample = data.reviews[0];
                console.log('[Debug] 첫 번째 리뷰 데이터 샘플:', sample);
                if (!sample.review_id && !sample.reviewId) {
                    console.error('[Critical] review_id 필드를 찾을 수 없습니다. 응답 필드명을 다시 확인하세요.');
                }
            }

            // 렌더링
            render_reviews(data.reviews);

            // 페이지네이션 처리
            update_header(data.reviews ? data.reviews.length : 0);
            render_simple_pagination(data.reviews);
        })
        .catch(error => {
            console.error('[API Error] 리뷰 목록 조회 오류:', error);
        });
}

/**
 * 리뷰 상세 조회
 * Method: GET
 * Path: /api/v1/reviews/{reviewId}
 */
function load_review_detail(review_id) {
    console.log('[Detail Request] 상세 조회 요청, ID:', review_id);

    if (review_id === undefined || review_id === null) {
        alert('오류: 유효하지 않은 리뷰 ID입니다.');
        return;
    }

    fetch(`${API_URL}/${review_id}`, {
        method: 'GET'
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('상세 조회 실패 Status: ' + response.status);
            }
            return response.json();
        })
        .then(data => {
            console.warn('============= [API 응답] 리뷰 상세 데이터 =============');
            console.log(data);
            console.warn('====================================================');

            alert(`리뷰 ID [${review_id}] 상세 조회 성공!\n데이터는 F12(개발자 도구) 콘솔을 확인하세요.`);
        })
        .catch(error => {
            console.error('리뷰 상세 조회 오류:', error);
            show_error('리뷰 정보를 불러오는데 실패했습니다.');
        });
}

/**
 * 리뷰 작성
 */
function create_review(review_data) {
    console.log('리뷰 작성 요청:', review_data);

    fetch(API_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(review_data)
    })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => {
                    throw new Error(err.message || '리뷰 작성 실패');
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('리뷰 작성 성공:', data);
            close_write_modal();
            show_success('리뷰가 성공적으로 작성되었습니다.');

            // 목록 새로고침
            current_page = 1;
            document.getElementById('page_input').value = 1;
            load_reviews();
        })
        .catch(error => {
            console.error('리뷰 작성 오류:', error);
            show_form_error('form_error_message', error.message);
        });
}

/**
 * 리뷰 수정
 */
function update_review(review_data) {
    fetch(API_URL + '/update', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(review_data)
    })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => {
                    throw new Error(err.message || '리뷰 수정 실패');
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('리뷰 수정 성공:', data);
            close_edit_modal();
            show_success('리뷰가 성공적으로 수정되었습니다.');
            load_reviews();
        })
        .catch(error => {
            console.error('리뷰 수정 오류:', error);
            show_form_error('edit_error_message', error.message);
        });
}

/**
 * 리뷰 삭제
 */
function delete_review(review_id) {
    fetch(API_URL + '/' + review_id, {
        method: 'DELETE'
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('리뷰 삭제 실패');
            }
            console.log('리뷰 삭제 성공');
            close_delete_confirm_modal();
            show_success('리뷰가 성공적으로 삭제되었습니다.');
            load_reviews();
        })
        .catch(error => {
            console.error('리뷰 삭제 오류:', error);
            show_error('리뷰 삭제에 실패했습니다.');
        });
}

// ========== 렌더링 함수 ==========

/**
 * 리뷰 목록 렌더링
 */
function render_reviews(reviews) {
    const container = document.getElementById('review_list_container');

    if (!reviews || reviews.length === 0) {
        container.innerHTML = `<div class="empty_state"><p>리뷰가 없습니다</p></div>`;
        return;
    }

    // 1. HTML 생성
    container.innerHTML = reviews.map(review => create_review_card(review)).join('');

    // 2. 클릭 이벤트 연결
    const cards = container.querySelectorAll('.review_card');
    cards.forEach((card, index) => {
        const review_data = reviews[index];
        const target_id = review_data.review_id || review_data.reviewId;

        card.addEventListener('click', () => {
            console.log(`[Click] 카드 클릭됨 (Index: ${index}, ID: ${target_id})`);
            if (target_id) {
                load_review_detail(target_id);
            } else {
                console.error('[Click Error] ID를 찾을 수 없습니다. 데이터:', review_data);
                alert('리뷰 ID를 찾을 수 없습니다.');
            }
        });
    });
}

/**
 * 리뷰 카드 HTML 생성
 */
function create_review_card(review) {
    // snake_case 우선 참조
    const stars = '⭐'.repeat(review.rating || 0);
    const raw_date = review.created_at || review.createdAt;
    const date_display = raw_date ? format_date(raw_date) : '-';
    const summary_text = review.summary || review.content || '내용 미리보기 없음';
    const summary_display = escape_html(summary_text);
    const tags_html = review.tags && review.tags.length > 0
        ? review.tags.map(tag => `<span class="tag">${escape_html(tag)}</span>`).join('')
        : '';
    const user_id_display = review.user_id || review.userId || '익명';
    const prop_name = review.property_name || review.propertyName || review.apt_nm || review.property_id || '매물명 미확인';

    return `
        <div class="review_card">
            <div class="review_card_header">
                <div class="review_rating">${stars}</div>
                <div class="review_date">${date_display}</div>
            </div>
            <div class="review_user">
                <i class="fas fa-user"></i>
                ${escape_html(user_id_display)}
            </div>
            <div class="review_summary" style="${!summary_text ? 'color:#ccc;' : ''}">
                ${summary_display}
            </div>
            ${tags_html ? `<div class="review_tags">${tags_html}</div>` : ''}
            <div class="review_property_info">
                <i class="fas fa-building"></i>
                ${escape_html(prop_name)}
            </div>
        </div>
    `;
}

/**
 * 단순 페이지네이션 렌더링
 */
function render_simple_pagination(reviews) {
    const container = document.getElementById('pagination_container');
    let html = '';

    const prev_disabled = current_page <= 1 ? 'disabled' : '';
    html += `<button class="pagination_btn" onclick="go_to_page(${current_page - 1})" ${prev_disabled}>
                <i class="fas fa-chevron-left"></i> 이전
             </button>`;

    html += `<div class="pagination_info">Page ${current_page}</div>`;

    const next_disabled = (!reviews || reviews.length < 10) ? 'disabled' : '';

    html += `<button class="pagination_btn" onclick="go_to_page(${current_page + 1})" ${next_disabled}>
                다음 <i class="fas fa-chevron-right"></i>
             </button>`;

    container.innerHTML = html;
}

/**
 * 헤더 정보 업데이트
 */
function update_header(count) {
    const el = document.getElementById('total_review_count');
    if(el) {
        el.parentElement.innerHTML = `현재 페이지 <span>${count}</span>개의 리뷰`;
    }
}

// ========== 모달 관련 함수 ==========

function open_write_modal() {
    document.getElementById('review_form').reset();
    hide_form_error('form_error_message');
    update_char_count('input_content', 'current_char_count');
    document.getElementById('write_review_modal').style.display = 'block';
}

function close_write_modal() {
    document.getElementById('write_review_modal').style.display = 'none';
}

function open_edit_modal(review_data) {
    const r_id = review_data.review_id || review_data.reviewId;
    document.getElementById('edit_review_id').value = r_id;
    document.getElementById('edit_rating').value = review_data.rating;
    document.getElementById('edit_content').value = review_data.content;

    update_char_count('edit_content', 'edit_char_count');
    hide_form_error('edit_error_message');

    document.getElementById('edit_review_modal').style.display = 'block';
}

function close_edit_modal() {
    document.getElementById('edit_review_modal').style.display = 'none';
}

function open_delete_confirm_modal(review_id) {
    current_review_id_for_delete = review_id;
    document.getElementById('delete_confirm_modal').style.display = 'block';
}

function close_delete_confirm_modal() {
    document.getElementById('delete_confirm_modal').style.display = 'none';
    current_review_id_for_delete = null;
}

function close_detail_modal() {
    document.getElementById('detail_review_modal').style.display = 'none';
    current_review_id_for_detail = null;
}

// ========== 폼 제출 핸들러 ==========

function submit_review(event) {
    event.preventDefault();
    const property_id = document.getElementById('input_property_id').value.trim();
    const user_id = document.getElementById('input_user_id').value.trim();
    const rating = parseInt(document.getElementById('input_rating').value);
    const content = document.getElementById('input_content').value.trim();

    if (property_id.length !== 32) {
        show_form_error('form_error_message', '매물 ID는 32자여야 합니다 (MD5).');
        return;
    }
    if (!rating) {
        show_form_error('form_error_message', '별점을 선택해주세요.');
        return;
    }
    if (content.length < 20) {
        show_form_error('form_error_message', '리뷰 내용은 최소 20자 이상이어야 합니다.');
        return;
    }

    const review_data = {
        propertyId: property_id,
        userId: user_id,
        rating: rating,
        content: content
    };

    create_review(review_data);
}

function submit_edit_review(event) {
    event.preventDefault();
    const review_id = parseInt(document.getElementById('edit_review_id').value);
    const rating = parseInt(document.getElementById('edit_rating').value);
    const content = document.getElementById('edit_content').value.trim();

    if (!rating) {
        show_form_error('edit_error_message', '별점을 선택해주세요.');
        return;
    }
    if (content.length < 20) {
        show_form_error('edit_error_message', '리뷰 내용은 최소 20자 이상이어야 합니다.');
        return;
    }

    const review_data = {
        reviewId: review_id,
        rating: rating,
        content: content
    };

    update_review(review_data);
}

function confirm_delete_review() {
    if (current_review_id_for_delete) {
        delete_review(current_review_id_for_delete);
    }
}

// ========== 필터 및 페이지 이동 함수 ==========

function apply_filters() {
    const page_input = document.getElementById('page_input').value;
    current_page = page_input ? parseInt(page_input) : 1;

    current_sort = document.getElementById('sort_select').value;

    const keyword_input = document.getElementById('keyword_search');
    current_keyword = keyword_input ? keyword_input.value.trim() : null;

    if (current_page < 1) {
        alert('페이지 번호는 1 이상이어야 합니다.');
        return;
    }

    load_reviews();
}

function clear_keyword_search() {
    const keyword_el = document.getElementById('keyword_search');
    if(keyword_el) keyword_el.value = '';

    const prop_name_el = document.getElementById('filter_property_name');
    if(prop_name_el) prop_name_el.value = '';

    current_keyword = null;
    current_page = 1;
    document.getElementById('page_input').value = 1;

    load_reviews();
}

function go_to_page(page) {
    if (page < 1) return;
    current_page = page;
    document.getElementById('page_input').value = page;
    load_reviews();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ========== 유틸리티 및 UI 함수 ==========

function toggle_sidebar() {
    const information = document.getElementById('information');
    const btn = document.getElementById('btn');
    const main_content = document.getElementById('main_content');

    if (information.style.left === '-480px' || information.style.left === '') {
        information.style.left = '0';
        btn.style.left = '475px';
        btn.innerHTML = '▼';
        main_content.style.marginLeft = '480px';
    } else {
        information.style.left = '-480px';
        btn.style.left = '10px';
        btn.innerHTML = '▶';
        main_content.style.marginLeft = '0';
    }
}

function format_date(date_string) {
    if (!date_string) return '-';
    const date = new Date(date_string);
    const now = new Date();
    const diff = now - date;

    if (diff < 60000) return '방금 전';
    if (diff < 3600000) return Math.floor(diff / 60000) + '분 전';
    if (diff < 86400000) return Math.floor(diff / 3600000) + '시간 전';
    if (diff < 604800000) return Math.floor(diff / 86400000) + '일 전';

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function escape_html(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function update_char_count(textarea_id, counter_id) {
    const textarea = document.getElementById(textarea_id);
    const counter = document.getElementById(counter_id);
    if (textarea && counter) {
        counter.textContent = textarea.value.length;
    }
}

function show_form_error(element_id, message) {
    const error_element = document.getElementById(element_id);
    if (error_element) {
        error_element.textContent = message;
        error_element.classList.add('show');
    }
}

function hide_form_error(element_id) {
    const error_element = document.getElementById(element_id);
    if (error_element) {
        error_element.textContent = '';
        error_element.classList.remove('show');
    }
}

function show_success(message) {
    alert(message);
}

function show_error(message) {
    alert('오류: ' + message);
}