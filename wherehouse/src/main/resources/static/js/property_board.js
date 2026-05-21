/**
 * property_board.js — F004 매물 게시판 + F002 수정 + F003 상태변경 UI 제어.
 *
 * 전역 변수 currentUserId 는 JSP 에서 주입됨 (null 이면 비인증).
 * Jackson SNAKE_CASE 전략에 의해 API 응답 키는 snake_case.
 */

var currentPage = 0;
var pageSize = 20;
var totalPages = 0;

// ============================================================
// 초기 로드
// ============================================================

document.addEventListener('DOMContentLoaded', function() {
    loadProperties();

    document.getElementById('filter_keyword').addEventListener('keydown', function(e) {
        if (e.key === 'Enter') applyFilter();
    });
});

// ============================================================
// API 호출 — 목록 조회
// ============================================================

function applyFilter() {
    currentPage = 0;
    loadProperties();
}

function loadProperties() {
    var params = new URLSearchParams();

    var leaseType = document.getElementById('filter_lease_type').value;
    var district = document.getElementById('filter_district').value;
    var status = document.getElementById('filter_status').value;
    var dataSource = document.getElementById('filter_data_source').value;
    var sort = document.getElementById('filter_sort').value;
    var keyword = document.getElementById('filter_keyword').value.trim();

    if (leaseType) params.append('leaseType', leaseType);
    if (district) params.append('district', district);
    if (status) params.append('status', status);
    if (dataSource) params.append('dataSource', dataSource);
    if (sort) params.append('sort', sort);
    if (keyword) params.append('keyword', keyword);
    params.append('page', currentPage);
    params.append('size', pageSize);

    fetch('/wherehouse/api/v1/properties?' + params.toString())
        .then(function(res) {
            if (!res.ok) throw new Error('목록 조회 실패: ' + res.status);
            return res.json();
        })
        .then(function(data) {
            totalPages = data.total_pages;
            renderCards(data.properties);
            renderPagination(data.current_page, data.total_pages);
            document.getElementById('result_count').textContent =
                '매물 ' + data.total_elements + '건';
            document.getElementById('result_page_info').textContent =
                (data.current_page + 1) + ' / ' + Math.max(data.total_pages, 1) + ' 페이지';
        })
        .catch(function(err) {
            console.error(err);
            document.getElementById('property_cards_container').innerHTML =
                '<div class="pb_empty_state">매물 목록을 불러올 수 없습니다.</div>';
        });
}

// ============================================================
// 카드 렌더링
// ============================================================

function renderCards(properties) {
    var container = document.getElementById('property_cards_container');

    if (!properties || properties.length === 0) {
        container.innerHTML = '<div class="pb_empty_state">조건에 맞는 매물이 없습니다.</div>';
        return;
    }

    var html = '';
    for (var i = 0; i < properties.length; i++) {
        html += buildCard(properties[i]);
    }
    container.innerHTML = html;
}

function buildCard(p) {
    var leaseLabel = p.lease_type === 'CHARTER' ? '전세' : '월세';
    var leaseBadge = p.lease_type === 'CHARTER' ? 'pb_badge_charter' : 'pb_badge_monthly';

    var sourceBadge = '';
    if (p.data_source === 'BATCH') sourceBadge = '<span class="pb_badge pb_badge_batch">배치</span>';
    else if (p.data_source === 'USER') sourceBadge = '<span class="pb_badge pb_badge_user">사용자</span>';
    else if (p.data_source === 'MERGED') sourceBadge = '<span class="pb_badge pb_badge_merged">병합</span>';

    var statusBadge = '';
    if (p.status === 'COMPLETED') statusBadge = '<span class="pb_badge pb_badge_completed">거래완료</span>';

    var priceText = formatPrice(p.deposit);
    if (p.lease_type === 'MONTHLY' && p.monthly_rent) {
        priceText += ' / 월 ' + formatPrice(p.monthly_rent);
    }

    var areaText = p.area_in_pyeong ? (parseFloat(p.area_in_pyeong).toFixed(1) + '평') : '-';

    // 버튼 영역
    var actionsHtml = '';
    var isOwner = !!(currentUserId && p.registered_user_id && currentUserId === p.registered_user_id);

    if (p.status === 'ACTIVE') {
        // 방문 예약 버튼은 ACTIVE 매물에 한해 노출.
        //  - 등록자 본인에게는 자기 매물 예약이 불가하므로 비표시.
        //  - 비로그인 사용자에게도 표시 — 클릭 시 로그인 유도.
        actionsHtml = '<div class="pb_card_actions" onclick="event.stopPropagation();">';

        if (!isOwner) {
            var aptNmEscaped = (p.apt_nm || '').replace(/'/g, "\\'");
            actionsHtml += '<button class="pb_card_btn pb_card_btn_reserve" onclick="window.openVisitSlotPicker(\'' +
                p.property_id + '\',\'' + p.lease_type + '\',\'' + aptNmEscaped + '\')">방문 예약</button>';
        }

        if (currentUserId && isOwner) {
            // 방문 예약 도입에 따른 정책 강화: 수정·상태 변경 모두 등록자 본인만 가능.
            actionsHtml += '<button class="pb_card_btn pb_card_btn_edit" onclick="openEditModal(\'' +
                p.property_id + '\',\'' + p.lease_type + '\',' + (p.deposit || '') + ',' +
                (p.monthly_rent || 'null') + ',' + (p.build_year || 'null') + ')">수정</button>';
            actionsHtml += '<button class="pb_card_btn pb_card_btn_complete" onclick="openStatusModal(\'' +
                p.property_id + '\',\'' + p.lease_type + '\',\'COMPLETED\')">거래완료</button>';
            actionsHtml += '<button class="pb_card_btn pb_card_btn_delete" onclick="openStatusModal(\'' +
                p.property_id + '\',\'' + p.lease_type + '\',\'DELETED\')">삭제</button>';
            // 방문 예약 슬롯 관리 진입
            actionsHtml += '<button class="pb_card_btn pb_card_btn_slots" onclick="window.location.href=\'/wherehouse/visit/me/slots?propertyId=' +
                p.property_id + '&leaseType=' + p.lease_type + '\'">슬롯 관리</button>';
        }
        actionsHtml += '</div>';
    }

    return '<div class="pb_card" onclick="openDetailModal(\'' + p.property_id + '\')">' +
        '<div class="pb_card_top">' +
            '<div class="pb_card_apt_nm">' + escapeHtml(p.apt_nm) + '</div>' +
            '<div class="pb_card_badges">' +
                '<span class="pb_badge ' + leaseBadge + '">' + leaseLabel + '</span>' +
                sourceBadge + statusBadge +
            '</div>' +
        '</div>' +
        '<div class="pb_card_body">' +
            '<div class="pb_card_address">' + escapeHtml(p.address || '') + '</div>' +
            '<div class="pb_card_info">' +
                '<div class="pb_card_info_item">' +
                    '<span class="pb_card_info_label">가격</span>' +
                    '<span class="pb_card_info_value pb_card_price">' + priceText + '</span>' +
                '</div>' +
                '<div class="pb_card_info_item">' +
                    '<span class="pb_card_info_label">면적</span>' +
                    '<span class="pb_card_info_value">' + areaText + '</span>' +
                '</div>' +
                '<div class="pb_card_info_item">' +
                    '<span class="pb_card_info_label">층</span>' +
                    '<span class="pb_card_info_value">' + (p.floor || '-') + '층</span>' +
                '</div>' +
                '<div class="pb_card_info_item">' +
                    '<span class="pb_card_info_label">지역구</span>' +
                    '<span class="pb_card_info_value">' + escapeHtml(p.district_name || '-') + '</span>' +
                '</div>' +
            '</div>' +
        '</div>' +
        actionsHtml +
    '</div>';
}

// ============================================================
// 페이지네이션
// ============================================================

function renderPagination(current, total) {
    var container = document.getElementById('pb_pagination');
    if (total <= 1) { container.innerHTML = ''; return; }

    var html = '';
    html += '<button class="pb_page_btn" onclick="goToPage(' + (current - 1) + ')" ' +
            (current === 0 ? 'disabled' : '') + '>&laquo;</button>';

    var start = Math.max(0, current - 4);
    var end = Math.min(total, start + 9);
    if (end - start < 9) start = Math.max(0, end - 9);

    for (var i = start; i < end; i++) {
        html += '<button class="pb_page_btn' + (i === current ? ' active' : '') +
                '" onclick="goToPage(' + i + ')">' + (i + 1) + '</button>';
    }

    html += '<button class="pb_page_btn" onclick="goToPage(' + (current + 1) + ')" ' +
            (current >= total - 1 ? 'disabled' : '') + '>&raquo;</button>';

    container.innerHTML = html;
}

function goToPage(page) {
    if (page < 0 || page >= totalPages) return;
    currentPage = page;
    loadProperties();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ============================================================
// 상세 조회 모달
// ============================================================

function openDetailModal(propertyId) {
    fetch('/wherehouse/api/v1/properties/' + propertyId)
        .then(function(res) {
            if (!res.ok) {
                if (res.status === 404) throw new Error('매물을 찾을 수 없습니다.');
                throw new Error('상세 조회 실패: ' + res.status);
            }
            return res.json();
        })
        .then(function(details) {
            renderDetailModal(details);
            document.getElementById('detail_modal').style.display = 'flex';
        })
        .catch(function(err) {
            alert(err.message);
        });
}

function renderDetailModal(details) {
    var body = document.getElementById('detail_modal_body');
    var html = '';

    for (var i = 0; i < details.length; i++) {
        var d = details[i];
        var s = d.summary;
        var leaseLabel = s.lease_type === 'CHARTER' ? '전세' : '월세';

        html += '<div class="pb_detail_section">';
        html += '<div class="pb_detail_title">' + escapeHtml(s.apt_nm) + ' (' + leaseLabel + ')</div>';
        html += '<div class="pb_detail_address">' + escapeHtml(s.address || '') + '</div>';

        if (s.status === 'COMPLETED') {
            html += '<div class="pb_error_msg" style="margin-bottom:12px;">거래완료된 매물입니다.</div>';
        }

        html += '<div class="pb_detail_grid">';
        html += detailItem('임대유형', leaseLabel);
        html += detailItem('지역구', s.district_name);
        html += detailItem('법정동', d.umd_nm);
        html += detailItem('지번', d.jibun);
        html += detailItem('시군구코드', d.sgg_cd);
        html += detailItem('층수', s.floor ? s.floor + '층' : '-');
        html += detailItem('전용면적', s.exclu_use_ar ? s.exclu_use_ar + '㎡' : '-');
        html += detailItem('평수', s.area_in_pyeong ? parseFloat(s.area_in_pyeong).toFixed(1) + '평' : '-');

        if (s.lease_type === 'CHARTER') {
            html += detailItem('전세금', formatPrice(s.deposit));
        } else {
            html += detailItem('보증금', formatPrice(s.deposit));
            html += detailItem('월세금', formatPrice(s.monthly_rent));
        }
        html += detailItem('건축연도', s.build_year || '-');
        html += detailItem('계약일자', d.deal_date || '-');
        html += detailItem('데이터출처', formatDataSource(s.data_source));
        html += detailItem('상태', formatStatus(s.status));
        html += detailItem('등록자', d.registered_user_id || '(배치)');
        html += detailItem('등록일', formatDateTime(s.registered_at));
        html += detailItem('수정일', formatDateTime(d.modified_at));
        html += '</div>';

        html += '<div class="pb_detail_review">';
        html += '<span>리뷰 ' + (d.review_count || 0) + '건</span>';
        html += '<span>평균 ★ ' + (d.avg_rating ? parseFloat(d.avg_rating).toFixed(1) : '0.0') + '</span>';
        html += '</div>';

        html += '</div>';

        if (i < details.length - 1) html += '<hr style="border:none;border-top:1px solid #edf0f5;margin:16px 0;">';
    }

    body.innerHTML = html;
}

function detailItem(label, value) {
    return '<div class="pb_detail_item"><span class="pb_detail_label">' + label +
           '</span><span class="pb_detail_value">' + escapeHtml(String(value || '-')) + '</span></div>';
}

function closeDetailModal() {
    document.getElementById('detail_modal').style.display = 'none';
}

// ============================================================
// 수정 모달
// ============================================================

function openEditModal(propertyId, leaseType, deposit, monthlyRent, buildYear) {
    document.getElementById('edit_property_id').value = propertyId;
    document.getElementById('edit_lease_type').value = leaseType;
    document.getElementById('edit_deposit').value = deposit || '';
    document.getElementById('edit_build_year').value = (buildYear && buildYear !== 'null') ? buildYear : '';
    document.getElementById('edit_deal_date').value = '';

    if (leaseType === 'MONTHLY') {
        document.getElementById('edit_monthly_rent_group').style.display = 'flex';
        document.getElementById('edit_monthly_rent').value = (monthlyRent && monthlyRent !== 'null') ? monthlyRent : '';
    } else {
        document.getElementById('edit_monthly_rent_group').style.display = 'none';
    }

    document.getElementById('edit_modal').style.display = 'flex';
}

function closeEditModal() {
    document.getElementById('edit_modal').style.display = 'none';
}

function submitEdit() {
    var propertyId = document.getElementById('edit_property_id').value;
    var leaseType = document.getElementById('edit_lease_type').value;
    var apiPath = leaseType === 'CHARTER' ? 'charter' : 'monthly';

    var body = {};
    var deposit = document.getElementById('edit_deposit').value;
    if (deposit) body.deposit = parseInt(deposit);

    var buildYear = document.getElementById('edit_build_year').value;
    if (buildYear) body.build_year = parseInt(buildYear);

    var dealDate = document.getElementById('edit_deal_date').value;
    if (dealDate) body.deal_date = dealDate;

    if (leaseType === 'MONTHLY') {
        var monthlyRent = document.getElementById('edit_monthly_rent').value;
        if (monthlyRent) body.monthly_rent = parseInt(monthlyRent);
    }

    fetch('/wherehouse/api/v1/properties/' + apiPath + '/' + propertyId, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(res) {
        if (res.status === 401) throw new Error('로그인이 필요합니다.');
        if (!res.ok) return res.json().then(function(e) { throw new Error(e.message || '수정 실패'); });
        return res.json();
    })
    .then(function(data) {
        closeEditModal();
        alert('매물이 수정되었습니다.');
        loadProperties();
    })
    .catch(function(err) {
        alert(err.message);
    });
}

// ============================================================
// 상태변경 모달
// ============================================================

function openStatusModal(propertyId, leaseType, targetStatus) {
    document.getElementById('status_property_id').value = propertyId;
    document.getElementById('status_lease_type').value = leaseType;
    document.getElementById('status_target').value = targetStatus;

    if (targetStatus === 'COMPLETED') {
        document.getElementById('status_modal_title').textContent = '거래완료 처리';
        document.getElementById('status_modal_message').textContent =
            '거래완료로 변경하시겠습니까? 변경 후 추천 검색에서 제외됩니다.';
    } else {
        document.getElementById('status_modal_title').textContent = '매물 삭제';
        document.getElementById('status_modal_message').textContent =
            '매물을 삭제하시겠습니까? 삭제 후 조회 및 검색에서 제외됩니다.';
    }

    document.getElementById('status_modal').style.display = 'flex';
}

function closeStatusModal() {
    document.getElementById('status_modal').style.display = 'none';
}

function submitStatusChange() {
    var propertyId = document.getElementById('status_property_id').value;
    var leaseType = document.getElementById('status_lease_type').value;
    var targetStatus = document.getElementById('status_target').value;
    var apiPath = leaseType === 'CHARTER' ? 'charter' : 'monthly';

    fetch('/wherehouse/api/v1/properties/' + apiPath + '/' + propertyId + '/status', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ target_status: targetStatus })
    })
    .then(function(res) {
        if (res.status === 401) throw new Error('로그인이 필요합니다.');
        if (res.status === 403) throw new Error('매물 등록자만 상태를 변경할 수 있습니다.');
        if (!res.ok) return res.json().then(function(e) { throw new Error(e.message || '상태변경 실패'); });
        return res.json();
    })
    .then(function(data) {
        closeStatusModal();
        alert(targetStatus === 'COMPLETED' ? '거래완료 처리되었습니다.' : '매물이 삭제되었습니다.');
        loadProperties();
    })
    .catch(function(err) {
        alert(err.message);
    });
}

// ============================================================
// 유틸
// ============================================================

function formatPrice(val) {
    if (!val && val !== 0) return '-';
    var num = parseInt(val);
    if (num >= 10000) {
        var eok = Math.floor(num / 10000);
        var remainder = num % 10000;
        return remainder > 0 ? eok + '억 ' + remainder.toLocaleString() + '만' : eok + '억';
    }
    return num.toLocaleString() + '만';
}

function formatDataSource(ds) {
    if (ds === 'BATCH') return '국토부 배치';
    if (ds === 'USER') return '사용자 등록';
    if (ds === 'MERGED') return '병합';
    return ds || '-';
}

function formatStatus(s) {
    if (s === 'ACTIVE') return '활성';
    if (s === 'COMPLETED') return '거래완료';
    if (s === 'DELETED') return '삭제';
    return s || '-';
}

function formatDateTime(dt) {
    if (!dt) return '-';
    return dt.replace('T', ' ').substring(0, 16);
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
