/**
 * visit_slot_picker.js — 방문 예약 슬롯 선택 모달 공통 모듈.
 *
 * 책임:
 *   - property_board.jsp / house_rec.jsp 등의 매물 카드 "방문 예약" 버튼이 이 모달을 호출한다.
 *   - 매물 식별자(+선택적 임대유형) 로 슬롯 목록을 조회하고, 슬롯별로 예약(F004) 또는
 *     재개방 알림 구독(F006) 을 실행한다.
 *
 * 외부 의존:
 *   - /wherehouse/css/visit_slot_picker.css (시각 스타일)
 *   - 페이지 전역 currentUserId (없으면 모달 호출 시 로그인 유도)
 *
 * 모달 HTML 은 본 스크립트가 최초 호출 시 body 에 동적 주입한다 — 각 JSP 에 마크업을 두지 않는다.
 */
(function () {
    var MODAL_ID = 'vsm_modal';
    var modalInjected = false;
    var currentContext = null;   // { propertyId, leaseType, propertyLabel }

    // ============================================================
    // 외부 진입점 — window.openVisitSlotPicker
    // ============================================================

    /**
     * 매물 카드의 "방문 예약" 버튼이 호출.
     *
     * @param {String} propertyId      매물 식별자 (32자)
     * @param {String} [leaseType]     'CHARTER' / 'MONTHLY' / null. null 이면 양 유형 모두 조회.
     * @param {String} [propertyLabel] 헤더에 표시할 매물명(선택).
     */
    window.openVisitSlotPicker = function (propertyId, leaseType, propertyLabel) {
        if (!ensureAuthenticated()) return;
        ensureModalInjected();
        currentContext = {
            propertyId: propertyId,
            leaseType: leaseType || null,
            propertyLabel: propertyLabel || ''
        };
        setHeaderLabel(currentContext.propertyLabel);
        clearAlerts();
        clearBody();
        showModal();
        loadSlotsAndRender();
    };

    // ============================================================
    // 인증 확인
    // ============================================================

    function ensureAuthenticated() {
        // 페이지가 currentUserId 를 노출하지 않은 경우 (예: house_rec.jsp), 클라이언트에서 단정할 수
        // 없으므로 일단 통과시키고 실제 예약/구독 API 응답의 401 로 처리한다.
        if (typeof window.currentUserId === 'undefined') return true;
        if (window.currentUserId) return true;
        promptLoginRedirect();
        return false;
    }

    function promptLoginRedirect() {
        if (confirm('방문 예약은 로그인 후 이용할 수 있습니다. 로그인 페이지로 이동하시겠습니까?')) {
            window.location.href = '/wherehouse/members/login';
        }
    }

    // ============================================================
    // 모달 마크업 주입
    // ============================================================

    function ensureModalInjected() {
        if (modalInjected) return;
        var html =
            '<div id="' + MODAL_ID + '">' +
              '<div class="vsm_overlay" onclick="window.closeVisitSlotPicker()"></div>' +
              '<div class="vsm_content">' +
                '<div class="vsm_header">' +
                  '<h3>방문 예약 — 슬롯 선택<span id="vsm_property_label"></span></h3>' +
                  '<button class="vsm_close" onclick="window.closeVisitSlotPicker()">&times;</button>' +
                '</div>' +
                '<div class="vsm_subtitle">예약 가능 시간을 선택하세요. 한 슬롯에 정확히 한 명만 확정됩니다.</div>' +
                '<div class="vsm_body">' +
                  '<div id="vsm_alerts"></div>' +
                  '<div id="vsm_lease_tabs"></div>' +
                  '<div id="vsm_slot_list"></div>' +
                '</div>' +
              '</div>' +
            '</div>';
        document.body.insertAdjacentHTML('beforeend', html);
        modalInjected = true;
    }

    window.closeVisitSlotPicker = function () {
        var el = document.getElementById(MODAL_ID);
        if (el) el.classList.remove('vsm_open');
        currentContext = null;
    };

    function showModal() {
        document.getElementById(MODAL_ID).classList.add('vsm_open');
    }

    function setHeaderLabel(label) {
        var span = document.getElementById('vsm_property_label');
        span.textContent = label ? ' (' + label + ')' : '';
    }

    // ============================================================
    // 슬롯 조회 + 렌더링
    // ============================================================

    function loadSlotsAndRender() {
        var url = '/wherehouse/api/v1/visit/properties/' + currentContext.propertyId + '/slots';
        if (currentContext.leaseType) url += '?lease_type=' + currentContext.leaseType;

        clearBody();
        showInfo('슬롯을 불러오는 중입니다...');

        fetch(url, { credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 404) throw new Error('매물을 찾을 수 없거나 비활성 상태입니다.');
                if (!res.ok) throw new Error('슬롯 조회 실패: ' + res.status);
                return res.json();
            })
            .then(function (data) {
                clearAlerts();
                renderSlots(data);
            })
            .catch(function (err) {
                showError(err.message);
                clearSlotList();
            });
    }

    function renderSlots(data) {
        var charterSlots = data.charter || [];
        var monthlySlots = data.monthly || [];
        var bothPresent = charterSlots.length > 0 && monthlySlots.length > 0;

        var tabsEl = document.getElementById('vsm_lease_tabs');
        tabsEl.innerHTML = '';

        var activeType = currentContext.leaseType;

        if (bothPresent && !activeType) activeType = 'CHARTER';
        if (!activeType) activeType = charterSlots.length > 0 ? 'CHARTER' : 'MONTHLY';

        if (bothPresent) {
            tabsEl.innerHTML =
                buildTab('CHARTER', '전세', activeType === 'CHARTER') +
                buildTab('MONTHLY', '월세', activeType === 'MONTHLY');
        }

        var listEl = document.getElementById('vsm_slot_list');
        listEl.innerHTML = '';

        var slots = activeType === 'CHARTER' ? charterSlots : monthlySlots;

        if (!slots || slots.length === 0) {
            listEl.innerHTML = '<div class="vsm_empty">현재 공개된 방문 슬롯이 없습니다.</div>';
            return;
        }

        var grouped = groupByDate(slots);
        var html = '';
        Object.keys(grouped).forEach(function (date) {
            html += '<div class="vsm_date_group">';
            html += '<div class="vsm_date_label">' + escapeHtml(formatDateLabel(date)) + '</div>';
            html += '<div class="vsm_slot_grid">';
            grouped[date].forEach(function (s) {
                html += buildSlotCard(s, activeType);
            });
            html += '</div>';
            html += '</div>';
        });
        listEl.innerHTML = html;
    }

    function buildTab(value, label, active) {
        return '<button class="vsm_lease_tab' + (active ? ' vsm_active' : '') +
               '" onclick="window.__vsmSwitchTab(\'' + value + '\')">' + label + '</button>';
    }

    window.__vsmSwitchTab = function (leaseType) {
        // 사용자가 탭을 누르면 그 유형으로 재조회 (서버가 양 유형 모두 같은 응답에 담아 보냈을 수 있지만,
        // currentContext 의 leaseType 만 갱신하고 같은 응답에서 다시 렌더하는 식으로 단순화)
        currentContext.leaseType = leaseType;
        loadSlotsAndRender();
    };

    function buildSlotCard(slot, leaseType) {
        var time = formatTime(slot.start_time) + ' ~ ' + formatTime(slot.end_time);
        var statusClass = slot.status === 'AVAILABLE' ? 'vsm_available' : 'vsm_reserved';
        var statusLabel = slot.status === 'AVAILABLE' ? '예약 가능' : '예약됨';

        var btnHtml = '';
        if (slot.status === 'AVAILABLE') {
            btnHtml = '<button class="vsm_slot_btn vsm_btn_reserve" onclick="window.__vsmReserve(' +
                slot.slot_id + ')">예약</button>';
        } else if (slot.status === 'RESERVED') {
            btnHtml = '<button class="vsm_slot_btn vsm_btn_subscribe" onclick="window.__vsmSubscribe(' +
                slot.slot_id + ')">재개방 알림 구독</button>';
        }

        return '<div class="vsm_slot_card' + (slot.status === 'RESERVED' ? ' vsm_reserved' : '') + '">' +
                 '<span class="vsm_slot_status ' + statusClass + '">' + statusLabel + '</span>' +
                 '<div class="vsm_slot_time">' + time + '</div>' +
                 btnHtml +
               '</div>';
    }

    // ============================================================
    // 예약 / 구독 액션
    // ============================================================

    window.__vsmReserve = function (slotId) {
        clearAlerts();
        showInfo('예약 처리 중...');

        fetch('/wherehouse/api/v1/visit/reservations', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ slot_id: slotId })
        })
            .then(parseJsonOrThrow)
            .then(function (data) {
                clearAlerts();
                renderConfirmedReservation(data);
            })
            .catch(function (err) {
                handleApiError(err);
            });
    };

    window.__vsmSubscribe = function (slotId) {
        clearAlerts();
        showInfo('구독 신청 중...');

        fetch('/wherehouse/api/v1/visit/slots/' + slotId + '/subscriptions', {
            method: 'POST',
            credentials: 'same-origin'
        })
            .then(parseJsonOrThrow)
            .then(function () {
                clearAlerts();
                showSuccess('재개방 알림 구독을 신청했습니다. 슬롯이 다시 열리면 알림으로 안내해 드립니다.');
            })
            .catch(handleApiError);
    };

    function renderConfirmedReservation(r) {
        var listEl = document.getElementById('vsm_slot_list');
        var registrant = r.registrant || {};
        var html =
            '<div class="vsm_alert vsm_alert_success">예약이 확정되었습니다.</div>' +
            '<div class="vsm_confirmed_box">' +
                '<dt>예약 ID</dt><dd>' + r.reservation_id + '</dd>' +
                '<dt>방문 일시</dt><dd>' + escapeHtml(formatDateTime(r.start_time)) +
                    ' ~ ' + escapeHtml(formatTime(r.end_time)) + '</dd>' +
                '<dt>임대 유형</dt><dd>' + (r.lease_type === 'CHARTER' ? '전세' : '월세') + '</dd>' +
                '<dt>등록자</dt><dd>' + escapeHtml(registrant.username || '-') + '</dd>' +
                '<dt>등록자 연락처</dt><dd>' + escapeHtml(registrant.contact || '-') + '</dd>' +
            '</div>' +
            '<div style="margin-top:14px;text-align:right;">' +
                '<button class="vsm_slot_btn vsm_btn_reserve" style="padding:6px 18px;" ' +
                'onclick="window.closeVisitSlotPicker()">닫기</button>' +
            '</div>';
        listEl.innerHTML = html;
    }

    function parseJsonOrThrow(res) {
        if (res.status === 401) {
            return res.text().then(function () {
                throw new Error('LOGIN_REQUIRED');
            });
        }
        return res.json().then(function (body) {
            if (!res.ok) {
                var err = new Error(body.message || ('요청 실패 (' + res.status + ')'));
                err.code = body.error_code;
                err.body = body;
                throw err;
            }
            return body;
        });
    }

    function handleApiError(err) {
        clearAlerts();
        if (err.message === 'LOGIN_REQUIRED') {
            showError('로그인이 필요합니다.');
            promptLoginRedirect();
            return;
        }
        showError(err.message || '요청 실패');

        // E7007/E7008/E7013 — 같은 매물·임대 유형의 대체 슬롯이 포함되어 있으면 그것을 다시 표시한다.
        if (err.body && err.body.available_slots) {
            var fakeResponse = {};
            var leaseField = (err.body.lease_type || currentContext.leaseType || 'CHARTER').toLowerCase();
            fakeResponse[leaseField] = err.body.available_slots;
            renderSlots(fakeResponse);
        } else {
            // 일반 실패면 슬롯 목록을 다시 갱신
            setTimeout(loadSlotsAndRender, 600);
        }
    }

    // ============================================================
    // 유틸 — 알림 / 그룹화 / 포맷
    // ============================================================

    function showInfo(msg) { putAlert(msg, 'vsm_alert_info'); }
    function showSuccess(msg) { putAlert(msg, 'vsm_alert_success'); }
    function showError(msg) { putAlert(msg, 'vsm_alert_error'); }

    function putAlert(message, cls) {
        var alertsEl = document.getElementById('vsm_alerts');
        if (!alertsEl) return;
        alertsEl.innerHTML = '<div class="vsm_alert ' + cls + '">' + escapeHtml(message) + '</div>';
    }

    function clearAlerts() {
        var alertsEl = document.getElementById('vsm_alerts');
        if (alertsEl) alertsEl.innerHTML = '';
    }

    function clearSlotList() {
        var listEl = document.getElementById('vsm_slot_list');
        if (listEl) listEl.innerHTML = '';
    }

    function clearBody() {
        clearSlotList();
        var tabsEl = document.getElementById('vsm_lease_tabs');
        if (tabsEl) tabsEl.innerHTML = '';
    }

    function groupByDate(slots) {
        var groups = {};
        slots.forEach(function (s) {
            var d = (s.start_time || '').substring(0, 10);
            if (!groups[d]) groups[d] = [];
            groups[d].push(s);
        });
        return groups;
    }

    function formatDateLabel(dateStr) {
        if (!dateStr) return '-';
        var d = new Date(dateStr + 'T00:00:00');
        if (isNaN(d.getTime())) return dateStr;
        var days = ['일', '월', '화', '수', '목', '금', '토'];
        return dateStr.replaceAll('-', '.') + ' (' + days[d.getDay()] + ')';
    }

    function formatTime(dt) {
        if (!dt) return '-';
        return dt.substring(11, 16);
    }

    function formatDateTime(dt) {
        if (!dt) return '-';
        return dt.replace('T', ' ').substring(0, 16);
    }

    function escapeHtml(text) {
        if (text == null) return '';
        var div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }
})();
