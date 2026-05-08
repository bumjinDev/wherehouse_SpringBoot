/**
 * 리뷰 게시판 JavaScript (Final Fixed Version)
 * - [Fix] close_detail_modal 함수 누락 수정
 * - [Fix] 페이지네이션 UI 개선 (페이지 번호 클릭 네비게이션)
 * - [Fix] #btn / toggle_sidebar 제거 (JSP에 해당 요소 없어 TypeError 발생하던 문제)
 * - 페이지 번호 필터링 정상 동작
 * - 디버깅 로그 포함
 *
 * [XSS 테스트] 아래 두 지점을 의도적으로 취약하게 변경함
 *   1. create_review_card() — escape_html() 제거, innerHTML로 content 직접 삽입
 *   2. load_review_detail() — textContent → innerHTML 변경
 *   원본 코드는 주석으로 보존됨 (// [원본] 표시)
 */

// ========== 전역 변수 ==========
// const BASE_URL = 'http://localhost:8185/wherehouse';    // 현재 줄은 로컬 환경이며, 배포 환경에서는 수정 필요
// const BASE_URL = 'http://wherehouse.it.kr:8185/wherehouse';  // 원격 미니 피씨.
const BASE_URL = window.location.origin + '/wherehouse';    // 통합 수정
const API_URL = BASE_URL + '/api/v1/reviews';
const SEARCH_API_URL = BASE_URL + '/api/v1/reviews/properties/search';

let current_page = 1;
let current_sort = 'rating_desc';
let current_keyword = null;

let current_review_id_for_delete = null;
let current_property_type_for_delete = null;
let current_review_id_for_detail = null;

let debounce_timer = null;

// ========== 초기화 ==========
window.onload = function() {
    console.log('[Init] 리뷰 게시판 초기화 시작');
    load_reviews();
    init_event_listeners();
    console.log('[Init] 리뷰 게시판 초기화 완료');
};

// ========== 이벤트 리스너 ==========
function init_event_listeners() {

    // [Debug] 필터 적용 버튼
    const applyBtn = document.getElementById('apply_filter_btn');
    if (applyBtn) {
        applyBtn.addEventListener('click', apply_filters);
    }

    document.getElementById('clear_keyword').addEventListener('click', clear_keyword_search);
    document.getElementById('write_review_btn').addEventListener('click', open_write_modal);
    document.getElementById('review_form').addEventListener('submit', submit_review);
    document.getElementById('review_edit_form').addEventListener('submit', submit_edit_review);
    document.getElementById('confirm_delete_btn').addEventListener('click', confirm_delete_review);

    // 글자수 세기 리스너
    const input_content = document.getElementById('input_content');
    if (input_content) {
        input_content.addEventListener('input', function() {
            update_char_count('input_content', 'current_char_count');
        });
    }
    const edit_content = document.getElementById('edit_content');
    if (edit_content) {
        edit_content.addEventListener('input', function() {
            update_char_count('edit_content', 'edit_char_count');
        });
    }

    // [자동완성] 사이드바 검색 필터
    const filter_input = document.getElementById('filter_property_name');
    if (filter_input) {
        filter_input.addEventListener('input', function() {
            trigger_autocomplete(this.value, 'filter_results', 'filter_property_name', null);
        });
        filter_input.addEventListener('focus', function() {
            if(this.value.trim().length >= 2) {
                trigger_autocomplete(this.value, 'filter_results', 'filter_property_name', null);
            }
        });
    }

    // [자동완성] 리뷰 작성 모달
    const modal_input = document.getElementById('input_property_name');
    if (modal_input) {
        modal_input.addEventListener('input', function() {
            document.getElementById('selected_property_id').value = '';
            trigger_autocomplete(this.value, 'modal_results', 'input_property_name', 'selected_property_id');
        });
    }

    // 외부 클릭 시 리스트 닫기
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.autocomplete_list') &&
            !e.target.closest('.filter_select') &&
            !e.target.closest('#input_property_name')) {
            close_all_autocomplete_lists();
        }
    });
}

// ========== 자동완성 로직 ==========

function trigger_autocomplete(keyword, list_id, input_id, hidden_id) {
    if (debounce_timer) clearTimeout(debounce_timer);

    if (!keyword || keyword.trim().length < 2) {
        const list = document.getElementById(list_id);
        if(list) list.style.display = 'none';
        return;
    }

    debounce_timer = setTimeout(() => {
        search_properties(keyword, list_id, input_id, hidden_id);
    }, 300);
}

function search_properties(keyword, list_id, input_id, hidden_id) {
    fetch(`${SEARCH_API_URL}?keyword=${encodeURIComponent(keyword.trim())}`, {
        method: 'GET'
    })
        .then(response => {
            if (!response.ok) throw new Error('검색 실패');
            return response.json();
        })
        .then(data => {
            render_search_results(data, list_id, input_id, hidden_id);
        })
        .catch(error => {
            console.error('[Autocomplete Error]', error);
        });
}

function render_search_results(results, list_id, input_id, hidden_id) {
    const list_el = document.getElementById(list_id);
    list_el.innerHTML = '';

    if (!results || results.length === 0) {
        list_el.style.display = 'none';
        return;
    }

    results.forEach(item => {
        const p_name = item.property_name || item.propertyName;
        const p_type = item.property_type || item.propertyType;
        const p_id   = item.property_id   || item.propertyId;

        const li = document.createElement('li');
        const badge_class = (p_type === '전세') ? 'charter' : 'monthly';

        li.innerHTML = `
            <span class="type_badge ${badge_class}">${p_type}</span>
            ${escape_html(p_name)}
        `;

        li.addEventListener('click', () => {
            const mapped_type = (p_type === '전세') ? 'charter' : 'monthly';
            select_search_result({
                propertyName: p_name,
                propertyId: p_id,
                propertyType: mapped_type
            }, list_id, input_id, hidden_id);
        });

        list_el.appendChild(li);
    });

    list_el.style.display = 'block';
}

function select_search_result(item, list_id, input_id, hidden_id) {
    const input_el = document.getElementById(input_id);
    if (input_el) {
        input_el.value = item.propertyName;
    }
    if (hidden_id) {
        const hidden_el = document.getElementById(hidden_id);
        if (hidden_el) {
            hidden_el.value = item.propertyId;
        }
        const type_el = document.getElementById('selected_property_type');
        if (type_el && item.propertyType) {
            type_el.value = item.propertyType;
            update_type_badge('selected_type_badge', 'selected_type_display', item.propertyType);
        }
    }
    document.getElementById(list_id).style.display = 'none';
}

function close_all_autocomplete_lists() {
    const lists = document.querySelectorAll('.autocomplete_list');
    lists.forEach(list => list.style.display = 'none');
}

// ========== API 호출 함수 ==========

function load_reviews() {
    console.group('🔍 [Debug] load_reviews 실행');
    console.log('0. 현재 전역 변수 current_page:', current_page);

    const prop_name_el = document.getElementById('filter_property_name');
    const sort_select_el = document.getElementById('sort_select');
    const keyword_search_el = document.getElementById('keyword_search');

    const prop_name_val = prop_name_el ? prop_name_el.value.trim() : '';
    const sort_val = sort_select_el ? sort_select_el.value : 'rating_desc';
    const keyword_val = keyword_search_el ? keyword_search_el.value.trim() : '';

    const prop_type_el = document.getElementById('property_type_filter');
    const prop_type_val = prop_type_el ? prop_type_el.value : '';

    console.log('1. 필터 값:', {
        propertyName: prop_name_val,
        propertyType: prop_type_val,
        sort: sort_val,
        keyword: keyword_val,
        page: current_page
    });

    const params = new URLSearchParams({
        page: current_page,
        sort: sort_val
    });

    if (prop_type_val) params.append('propertyType', prop_type_val);
    if (prop_name_val) params.append('propertyName', prop_name_val);
    if (keyword_val) params.append('keyword', keyword_val);

    const finalUrl = `${API_URL}/list?${params.toString()}`;
    console.log('2. 요청 URL:', finalUrl);

    fetch(finalUrl, { method: 'GET' })
        .then(res => {
            console.log('3. 응답 상태:', res.status);
            return res.ok ? res.json() : Promise.reject(res);
        })
        .then(data => {
            console.log('4. 수신 데이터:', data);
            render_reviews(data.reviews);
            update_header(data.reviews ? data.reviews.length : 0, data.total_elements);
            render_pagination(data);
            console.groupEnd();
        })
        .catch(err => {
            console.error('❌ API 호출 실패:', err);
            console.groupEnd();
        });
}

function apply_filters() {
    console.log('🔘 필터 적용 버튼 클릭');

    const pageInput = document.getElementById('page_input');

    if (pageInput) {
        const inputVal = parseInt(pageInput.value);
        console.log(`입력된 페이지 번호: ${inputVal}`);

        if (!isNaN(inputVal) && inputVal > 0) {
            current_page = inputVal;
        } else {
            console.warn('유효하지 않은 페이지 번호 -> 1로 초기화');
            current_page = 1;
            pageInput.value = 1;
        }
    } else {
        console.error('page_input 요소를 찾을 수 없습니다.');
        current_page = 1;
    }

    console.log(`최종 적용 페이지: ${current_page}`);
    load_reviews();
}

// ========== 상세, 작성, 수정, 삭제 로직 ==========

function load_review_detail(review_id) {
    if (!review_id) return alert('오류: ID 없음');

    fetch(`${API_URL}/${review_id}`, { method: 'GET' })
        .then(res => res.json())
        .then(data => {
            console.log('상세 데이터:', data);

            // 상세 모달에 데이터 바인딩
            document.getElementById('detail_user_id').textContent = data.userId || data.user_id || '익명';
            document.getElementById('detail_property_id').textContent = data.propertyId || data.property_id || '-';

            const detailType = data.propertyType || data.property_type || '';
            const detailTypeLabel = (detailType === 'charter' || detailType === '전세') ? '전세'
                : (detailType === 'monthly' || detailType === '월세') ? '월세' : '-';
            document.getElementById('detail_property_type').textContent = detailTypeLabel;

            document.getElementById('detail_rating').textContent = '⭐'.repeat(data.rating || 0) + ` (${data.rating}점)`;
            document.getElementById('detail_created_at').textContent = (data.createdAt || data.created_at || '').replace('T', ' ');

            // ================================================================
            // [XSS 테스트] 지점 2: 상세 모달 — content를 HTML로 해석하도록 변경
            // [원본] document.getElementById('detail_content').textContent = data.content || '';
            document.getElementById('detail_content').innerHTML = data.content || '';
            // ================================================================

            // 태그 바인딩
            const tagsDiv = document.getElementById('detail_tags');
            tagsDiv.innerHTML = '';
            if (data.tags && data.tags.length > 0) {
                data.tags.forEach(tag => {
                    tagsDiv.innerHTML += `<span class="tag">#${tag}</span> `;
                });
            }

            // 수정/삭제 버튼 이벤트 연결
            const editBtn = document.getElementById('btn_edit_review');
            const delBtn = document.getElementById('btn_delete_review');

            const detailPropertyType = data.propertyType || data.property_type;

            editBtn.onclick = function() {
                close_detail_modal();
                open_edit_modal(data);
            };
            delBtn.onclick = function() {
                close_detail_modal();
                open_delete_confirm_modal(data.reviewId || data.review_id, detailPropertyType);
            };

            document.getElementById('detail_review_modal').style.display = 'block';
        })
        .catch(err => {
            console.error(err);
            alert('상세 조회 실패');
        });
}

function create_review(review_data) {
    console.log('[Review Submit] 전송:', review_data);

    fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(review_data)
    })
        .then(async res => {
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || '작성 실패');
            }
            return res.json();
        })
        .then(() => {
            alert('작성 완료');
            close_write_modal();
            load_reviews();
        })
        .catch(err => {
            alert(err.message);
        });
}

function update_review(review_data) {
    console.log('[Review Update] 전송:', review_data);

    fetch(API_URL + '/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(review_data)
    })
        .then(async res => {
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || '수정 실패');
            }
            return res.json();
        })
        .then(() => {
            alert('수정 완료');
            close_edit_modal();
            load_reviews();
        })
        .catch(err => {
            show_form_error('edit_error_message', err.message);
        });
}

function delete_review(review_id, propertyType) {
    fetch(API_URL + '/' + review_id + '?propertyType=' + encodeURIComponent(propertyType), { method: 'DELETE' })
        .then(res => {
            if(!res.ok) throw new Error('삭제 실패');
            close_delete_confirm_modal();
            alert('삭제 완료');
            load_reviews();
        })
        .catch(err => alert('삭제 실패'));
}

// ========== 렌더링 함수 ==========

function render_reviews(reviews) {
    const container = document.getElementById('review_list_container');
    if (!reviews || reviews.length === 0) {
        container.innerHTML = `<div class="empty_state"><p>리뷰가 없습니다</p></div>`;
        return;
    }
    container.innerHTML = reviews.map(review => create_review_card(review)).join('');

    container.querySelectorAll('.review_card').forEach((card, index) => {
        const data = reviews[index];
        const id = data.review_id || data.reviewId;
        card.addEventListener('click', () => id ? load_review_detail(id) : alert('ID 오류'));
    });
}

function create_review_card(review) {
    const stars = '⭐'.repeat(review.rating || 0);
    const date = review.created_at ? format_date(review.created_at) : '-';

    // ====================================================================
    // [XSS 테스트] 지점 1: 리뷰 카드 — escape_html() 제거, content를 그대로 삽입
    // [원본] const summary = escape_html(review.summary || review.content || '내용 없음');
    const summary = review.summary || review.content || '내용 없음';
    // ====================================================================

    const user = escape_html(review.user_id || review.userId || '익명');
    const prop = escape_html(review.property_name || review.propertyName || review.apt_nm || '미확인');
    const tags = (review.tags || []).map(t => `<span class="tag">${escape_html(t)}</span>`).join('');

    const rawType = review.property_type || review.propertyType || '';
    const isCharter = (rawType === 'charter' || rawType === '전세');
    const typeBadge = rawType
        ? `<span class="type_badge ${isCharter ? 'charter' : 'monthly'}">${isCharter ? '전세' : '월세'}</span>`
        : '';

    return `
        <div class="review_card">
            <div class="review_card_header">
                <div class="review_rating">${stars}</div>
                <div class="review_date">${date}</div>
            </div>
            <div class="review_user"><i class="fas fa-user"></i> ${user}</div>
            <div class="review_summary">${summary}</div>
            <div class="review_tags">${tags}</div>
            <div class="review_property_info"><i class="fas fa-building"></i> ${typeBadge} ${prop}</div>
        </div>
    `;
}

/**
 * [수정됨] 페이지네이션 렌더링 — 페이지 번호 클릭 네비게이션
 *
 * 백엔드 응답의 total_pages, total_elements, current_page 사용
 * UI: « ‹ ... 3 4 [5] 6 7 ... › »
 */
function render_pagination(data) {
    const container = document.getElementById('pagination_container');
    const totalPages = data.total_pages || 1;
    const currentPage = data.current_page || current_page;
    const totalElements = data.total_elements || 0;

    // 페이지가 1 이하면 페이지네이션 불필요
    if (totalPages <= 1) {
        container.innerHTML = '<div class="pagination_total">총 ' + totalElements + '개 리뷰</div>';
        return;
    }

    let html = '';

    // 총 리뷰 수 표시
    html += '<div class="pagination_total">총 ' + totalElements + '개</div>';

    // « 첫 페이지 버튼
    var firstDisabled = currentPage <= 1 ? 'disabled' : '';
    html += '<button class="pagination_btn" onclick="go_to_page(1)" ' + firstDisabled + '>&laquo;</button>';

    // ‹ 이전 페이지 버튼
    html += '<button class="pagination_btn" onclick="go_to_page(' + (currentPage - 1) + ')" ' + firstDisabled + '>&lsaquo;</button>';

    // 페이지 번호 그룹 계산 (현재 페이지 기준 최대 5개)
    var startPage = Math.max(1, currentPage - 2);
    var endPage = Math.min(totalPages, startPage + 4);

    // startPage 재조정 (endPage가 totalPages에 닿은 경우)
    if (endPage - startPage < 4) {
        startPage = Math.max(1, endPage - 4);
    }

    // 앞 생략 표시
    if (startPage > 1) {
        html += '<span class="pagination_ellipsis">...</span>';
    }

    // 페이지 번호 버튼들
    for (var i = startPage; i <= endPage; i++) {
        var activeClass = (i === currentPage) ? ' active' : '';
        html += '<button class="pagination_btn' + activeClass + '" onclick="go_to_page(' + i + ')">' + i + '</button>';
    }

    // 뒤 생략 표시
    if (endPage < totalPages) {
        html += '<span class="pagination_ellipsis">...</span>';
    }

    // › 다음 페이지 버튼
    var lastDisabled = currentPage >= totalPages ? 'disabled' : '';
    html += '<button class="pagination_btn" onclick="go_to_page(' + (currentPage + 1) + ')" ' + lastDisabled + '>&rsaquo;</button>';

    // » 마지막 페이지 버튼
    html += '<button class="pagination_btn" onclick="go_to_page(' + totalPages + ')" ' + lastDisabled + '>&raquo;</button>';

    container.innerHTML = html;
}

/**
 * [수정됨] 헤더 업데이트 — 전체 리뷰 수 표시
 */
function update_header(count, totalElements) {
    const countDiv = document.querySelector('.total_count');
    if (countDiv) {
        if (totalElements != null) {
            countDiv.innerHTML = '전체 <span>' + totalElements + '</span>개 리뷰';
        } else {
            countDiv.innerHTML = '현재 페이지 <span>' + count + '</span>개';
        }
    }
}

// ========== 모달 & 폼 핸들러 ==========

function open_write_modal() {
    document.getElementById('review_form').reset();
    document.getElementById('modal_results').style.display = 'none';

    const hidden_id = document.getElementById('selected_property_id');
    if(hidden_id) hidden_id.value = '';
    const hidden_type = document.getElementById('selected_property_type');
    if(hidden_type) hidden_type.value = '';
    const type_display = document.getElementById('selected_type_display');
    if(type_display) type_display.style.display = 'none';

    hide_form_error('form_error_message');
    update_char_count('input_content', 'current_char_count');
    document.getElementById('write_review_modal').style.display = 'block';
}

function close_write_modal() { document.getElementById('write_review_modal').style.display = 'none'; }

function open_edit_modal(data) {
    const r_id = data.reviewId || data.review_id;
    document.getElementById('edit_review_id').value = r_id;
    const editType = data.propertyType || data.property_type || '';
    document.getElementById('edit_property_type').value = editType;
    update_type_badge('edit_type_badge', 'edit_type_display', editType);

    document.getElementById('edit_rating').value = data.rating;
    document.getElementById('edit_content').value = data.content;
    update_char_count('edit_content', 'edit_char_count');

    hide_form_error('edit_error_message');
    document.getElementById('edit_review_modal').style.display = 'block';
}

function close_edit_modal() { document.getElementById('edit_review_modal').style.display = 'none'; }

function open_delete_confirm_modal(id, propertyType) {
    current_review_id_for_delete = id;
    current_property_type_for_delete = propertyType || null;
    document.getElementById('delete_confirm_modal').style.display = 'block';
}

function close_delete_confirm_modal() {
    document.getElementById('delete_confirm_modal').style.display = 'none';
}

// [누락되었던 함수 추가됨]
function close_detail_modal() {
    document.getElementById('detail_review_modal').style.display = 'none';
}

// ========== 이벤트 핸들러 (폼 제출 등) ==========

function submit_review(e) {
    e.preventDefault();

    const propId = document.getElementById('selected_property_id').value;
    const propType = document.getElementById('selected_property_type').value;
    const rating = document.getElementById('input_rating').value;
    const content = document.getElementById('input_content').value;

    if (!propId) {
        alert('매물을 검색하여 목록에서 선택해주세요.');
        return;
    }

    const payload = {
        propertyId: propId,
        propertyType: propType,
        rating: parseInt(rating),
        content: content
    };

    create_review(payload);
}

function submit_edit_review(e) {
    e.preventDefault();

    const reviewId = document.getElementById('edit_review_id').value;
    const propType = document.getElementById('edit_property_type').value;
    const rating = document.getElementById('edit_rating').value;
    const content = document.getElementById('edit_content').value;

    const payload = {
        reviewId: parseInt(reviewId),
        propertyType: propType,
        rating: parseInt(rating),
        content: content
    };

    update_review(payload);
}

function confirm_delete_review() {
    if(current_review_id_for_delete && current_property_type_for_delete) {
        delete_review(current_review_id_for_delete, current_property_type_for_delete);
    }
}

function clear_keyword_search() {
    document.getElementById('keyword_search').value = '';
    const filter_prop = document.getElementById('filter_property_name');
    if(filter_prop) filter_prop.value = '';
    current_keyword = null;
    load_reviews();
}

/**
 * [수정됨] 페이지 이동 — 범위 방어 로직 추가
 */
function go_to_page(p) {
    if (p < 1) return;

    console.log('[Pagination] 페이지 이동 요청: ' + p);
    current_page = p;

    const pageInput = document.getElementById('page_input');
    if (pageInput) {
        pageInput.value = p;
    }

    load_reviews();
    window.scrollTo({top:0, behavior:'smooth'});
}

// ========== 유틸리티 ==========

function format_date(s) {
    if(!s) return '-';
    const d = new Date(s);
    const now = new Date();
    const diff = now - d;

    if(diff < 60000) return '방금 전';
    if(diff < 3600000) return Math.floor(diff/60000) + '분 전';
    if(diff < 86400000) return Math.floor(diff/3600000) + '시간 전';

    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

function escape_html(t) {
    if(!t) return '';
    const d = document.createElement('div');
    d.textContent = t;
    return d.innerHTML;
}

function update_char_count(tid, cid) {
    document.getElementById(cid).textContent = document.getElementById(tid).value.length;
}

function update_type_badge(badge_id, display_id, type) {
    const badge = document.getElementById(badge_id);
    const display = document.getElementById(display_id);
    if (!badge) return;

    const isCharter = (type === 'charter' || type === '전세');
    badge.className = 'type_badge ' + (isCharter ? 'charter' : 'monthly');
    badge.textContent = isCharter ? '전세' : '월세';
    if (display) display.style.display = type ? 'block' : 'none';
}

function show_form_error(eid, msg) {
    const e = document.getElementById(eid);
    if(e) { e.textContent = msg; e.classList.add('show'); }
}

function hide_form_error(eid) {
    const e = document.getElementById(eid);
    if(e) { e.textContent = ''; e.classList.remove('show'); }
}