/**
 * 리뷰 게시판 JavaScript (Snake Case 대응 & Property ID 전송 수정 버전)
 */

// ========== 전역 변수 ==========
const BASE_URL = 'http://localhost:8185/wherehouse';
const API_URL = BASE_URL + '/api/v1/reviews';
const SEARCH_API_URL = BASE_URL + '/api/v1/properties/search';

let current_page = 1;
let current_sort = 'rating_desc';
let current_keyword = null;

let current_review_id_for_delete = null;
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
    document.getElementById('btn').addEventListener('click', toggle_sidebar);
    document.getElementById('apply_filter_btn').addEventListener('click', apply_filters);
    document.getElementById('clear_keyword').addEventListener('click', clear_keyword_search);
    document.getElementById('write_review_btn').addEventListener('click', open_write_modal);
    document.getElementById('review_form').addEventListener('submit', submit_review);
    document.getElementById('review_edit_form').addEventListener('submit', submit_edit_review);
    document.getElementById('confirm_delete_btn').addEventListener('click', confirm_delete_review);

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

    // [자동완성] 사이드바 검색 필터 (이름으로 검색 -> filtering)
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

    // [자동완성] 리뷰 작성 모달 입력창 (ID 선택 필수)
    const modal_input = document.getElementById('input_property_name');
    if (modal_input) {
        modal_input.addEventListener('input', function() {
            // 입력 시 hidden ID 값을 초기화하여 오입력 방지
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

// ========== 자동완성 로직 (Snake Case 수정됨) ==========

function trigger_autocomplete(keyword, list_id, input_id, hidden_id) {
    if (debounce_timer) clearTimeout(debounce_timer);

    if (!keyword || keyword.trim().length < 2) {
        const list = document.getElementById(list_id);
        if(list) list.style.display = 'none';
        return;
    }

    // 300ms 디바운싱
    debounce_timer = setTimeout(() => {
        search_properties(keyword, list_id, input_id, hidden_id);
    }, 300);
}

function search_properties(keyword, list_id, input_id, hidden_id) {
    console.log(`[Autocomplete] API 요청: ${keyword}`);

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

    // console.log('[Autocomplete] 데이터 수신:', results);

    results.forEach(item => {
        // 서버의 Snake Case 키값을 우선 참조
        const p_name = item.property_name || item.propertyName;
        const p_type = item.property_type || item.propertyType;
        const p_id   = item.property_id   || item.propertyId;

        const li = document.createElement('li');

        // 타입에 따른 뱃지 스타일
        const badge_class = (p_type === '전세') ? 'charter' : 'monthly';

        li.innerHTML = `
            <span class="type_badge ${badge_class}">${p_type}</span>
            ${escape_html(p_name)}
        `;

        // 클릭 시 정제된 데이터를 넘김
        li.addEventListener('click', () => {
            select_search_result({
                propertyName: p_name,
                propertyId: p_id
            }, list_id, input_id, hidden_id);
        });

        list_el.appendChild(li);
    });

    list_el.style.display = 'block';
}

function select_search_result(item, list_id, input_id, hidden_id) {
    // 1. 입력창에 이름 채우기
    const input_el = document.getElementById(input_id);
    if (input_el) {
        input_el.value = item.propertyName;
    }

    // 2. 히든 필드에 ID 저장 (리뷰 작성 시 핵심)
    if (hidden_id) {
        const hidden_el = document.getElementById(hidden_id);
        if (hidden_el) {
            hidden_el.value = item.propertyId;
            console.log(`[Autocomplete] ID 선택됨: ${item.propertyId} (${item.propertyName})`);
        }
    }

    // 3. 리스트 닫기
    document.getElementById(list_id).style.display = 'none';
}

function close_all_autocomplete_lists() {
    const lists = document.querySelectorAll('.autocomplete_list');
    lists.forEach(list => list.style.display = 'none');
}


// ========== API 호출 함수 ==========

function load_reviews() {
    const prop_name_input = document.getElementById('filter_property_name');
    const prop_name_val = prop_name_input ? prop_name_input.value.trim() : null;

    console.log('[API Request] 목록 조회:', { page: current_page, propertyName: prop_name_val });

    const params = new URLSearchParams({ page: current_page, sort: current_sort });
    if (prop_name_val) params.append('propertyName', prop_name_val);
    if (current_keyword) params.append('keyword', current_keyword);

    fetch(`${API_URL}/list?${params.toString()}`, { method: 'GET' })
        .then(res => res.ok ? res.json() : Promise.reject(res))
        .then(data => {
            render_reviews(data.reviews);
            update_header(data.reviews ? data.reviews.length : 0);
            render_simple_pagination(data.reviews);
        })
        .catch(console.error);
}

function load_review_detail(review_id) {
    if (!review_id) return alert('오류: ID 없음');
    fetch(`${API_URL}/${review_id}`, { method: 'GET' })
        .then(res => res.json())
        .then(data => {
            console.warn('============= 상세 데이터 =============');
            console.log(data);
            alert(`리뷰 ID [${review_id}] 조회 성공! 콘솔 확인`);
        })
        .catch(err => alert('상세 조회 실패'));
}

function create_review(review_data) {
    // [수정] propertyId를 포함한 JSON 전송
    console.log('[Review Submit] 전송 데이터:', review_data);

    fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(review_data)
    })
        .then(res => res.ok ? res.json() : res.json().then(e => Promise.reject(e)))
        .then(data => {
            close_write_modal();
            alert('작성 완료');
            current_page = 1;
            load_reviews();
        })
        .catch(err => show_form_error('form_error_message', err.message || '작성 실패'));
}

function update_review(review_data) {
    // 수정 로직 (필요 시 구현)
    fetch(API_URL + '/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(review_data)
    })
        .then(res => res.ok ? res.json() : res.json().then(e => Promise.reject(e)))
        .then(data => {
            close_edit_modal();
            alert('수정 완료');
            load_reviews();
        })
        .catch(err => show_form_error('edit_error_message', err.message));
}

function delete_review(review_id) {
    fetch(API_URL + '/' + review_id, { method: 'DELETE' })
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
    const raw_date = review.created_at || review.createdAt;
    const date = raw_date ? format_date(raw_date) : '-';
    const summary = escape_html(review.summary || review.content || '내용 없음');
    const user = escape_html(review.user_id || review.userId || '익명');
    const prop = escape_html(review.property_name || review.propertyName || review.apt_nm || '미확인');
    const tags = (review.tags || []).map(t => `<span class="tag">${escape_html(t)}</span>`).join('');

    return `
        <div class="review_card">
            <div class="review_card_header">
                <div class="review_rating">${stars}</div>
                <div class="review_date">${date}</div>
            </div>
            <div class="review_user"><i class="fas fa-user"></i> ${user}</div>
            <div class="review_summary">${summary}</div>
            <div class="review_tags">${tags}</div>
            <div class="review_property_info"><i class="fas fa-building"></i> ${prop}</div>
        </div>
    `;
}

function render_simple_pagination(reviews) {
    const container = document.getElementById('pagination_container');
    let html = '';
    const prev_disabled = current_page <= 1 ? 'disabled' : '';
    html += `<button class="pagination_btn" onclick="go_to_page(${current_page - 1})" ${prev_disabled}>이전</button>`;
    html += `<div class="pagination_info">Page ${current_page}</div>`;
    const next_disabled = (!reviews || reviews.length < 10) ? 'disabled' : '';
    html += `<button class="pagination_btn" onclick="go_to_page(${current_page + 1})" ${next_disabled}>다음</button>`;
    container.innerHTML = html;
}

function update_header(count) {
    const el = document.getElementById('total_review_count');
    if(el) el.parentElement.innerHTML = `현재 페이지 <span>${count}</span>개`;
}

// ========== 모달 & 폼 핸들러 ==========

function open_write_modal() {
    document.getElementById('review_form').reset();
    document.getElementById('modal_results').style.display = 'none';

    // [중요] 모달 열 때 이전 선택된 ID 초기화
    const hidden_id = document.getElementById('selected_property_id');
    if(hidden_id) hidden_id.value = '';

    hide_form_error('form_error_message');
    update_char_count('input_content', 'current_char_count');
    document.getElementById('write_review_modal').style.display = 'block';
}

function close_write_modal() { document.getElementById('write_review_modal').style.display = 'none'; }

function open_edit_modal(data) {
    const r_id = data.review_id || data.reviewId;
    document.getElementById('edit_review_id').value = r_id;
    // ... 나머지 필드 매핑 ...
    document.getElementById('edit_review_modal').style.display = 'block';
}
function close_edit_modal() { document.getElementById('edit_review_modal').style.display = 'none'; }

function open_delete_confirm_modal(id) { current_review_id_for_delete = id; document.getElementById('delete_confirm_modal').style.display = 'block'; }
function close_delete_confirm_modal() { document.getElementById('delete_confirm_modal').style.display = 'none'; }
function close_detail_modal() { document.getElementById('detail_review_modal').style.display = 'none'; }

// [핵심 수정 함수] 리뷰 작성 제출
function submit_review(e) {
    e.preventDefault();

    // 1. Hidden Input에서 ID 가져오기
    const prop_id_input = document.getElementById('selected_property_id');
    const prop_id = prop_id_input ? prop_id_input.value : null;

    // UI상 텍스트 (유효성 검증 보조)
    const prop_name_display = document.getElementById('input_property_name').value.trim();

    const rating = parseInt(document.getElementById('input_rating').value);
    const content = document.getElementById('input_content').value.trim();

    // 2. ID 유효성 검사 (검색 후 리스트에서 클릭했는지 확인)
    if (!prop_id || prop_name_display.length < 2) {
        show_form_error('form_error_message', '매물을 검색하여 목록에서 선택해주세요.');
        return;
    }

    if (!rating) {
        show_form_error('form_error_message', '별점 선택');
        return;
    }
    if (content.length < 20) {
        show_form_error('form_error_message', '내용 부족 (최소 20자)');
        return;
    }

    // 3. propertyName 대신 propertyId 전송
    create_review({
        propertyId: prop_id,  // [변경] String Name -> Long ID
        rating: rating,
        content: content
    });
}

function submit_edit_review(e) { e.preventDefault(); /* ... */ }
function confirm_delete_review() { if(current_review_id_for_delete) delete_review(current_review_id_for_delete); }

function apply_filters() { load_reviews(); }
function clear_keyword_search() {
    document.getElementById('keyword_search').value = '';
    const filter_prop = document.getElementById('filter_property_name');
    if(filter_prop) filter_prop.value = '';
    current_keyword = null;
    load_reviews();
}
function go_to_page(p) { current_page = p; load_reviews(); window.scrollTo({top:0, behavior:'smooth'}); }

// ========== 유틸리티 ==========
function toggle_sidebar() {
    const info = document.getElementById('information');
    const btn = document.getElementById('btn');
    const main = document.getElementById('main_content');
    if (info.style.left === '-480px' || !info.style.left) {
        info.style.left = '0'; btn.style.left = '475px'; btn.innerHTML = '▼'; main.style.marginLeft = '480px';
    } else {
        info.style.left = '-480px'; btn.style.left = '10px'; btn.innerHTML = '▶'; main.style.marginLeft = '0';
    }
}
function format_date(s) {
    if(!s) return '-'; const d = new Date(s), now = new Date(), diff = now - d;
    if(diff < 60000) return '방금 전';
    if(diff < 3600000) return Math.floor(diff/60000) + '분 전';
    if(diff < 86400000) return Math.floor(diff/3600000) + '시간 전';
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}
function escape_html(t) { if(!t) return ''; const d = document.createElement('div'); d.textContent = t; return d.innerHTML; }
function update_char_count(tid, cid) { document.getElementById(cid).textContent = document.getElementById(tid).value.length; }
function show_form_error(eid, msg) { const e = document.getElementById(eid); if(e) { e.textContent = msg; e.classList.add('show'); } }
function hide_form_error(eid) { const e = document.getElementById(eid); if(e) { e.textContent = ''; e.classList.remove('show'); } }
function show_success(msg) { alert(msg); }
function show_error(msg) { alert('오류: ' + msg); }