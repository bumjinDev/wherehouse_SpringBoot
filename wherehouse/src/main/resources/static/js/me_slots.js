/**
 * me_slots.js — 등록자 매물 슬롯 관리 페이지.
 *
 * 진입:
 *   /wherehouse/visit/me/slots                                                 (가이드)
 *   /wherehouse/visit/me/slots?propertyId=XXX[&leaseType=CHARTER|MONTHLY]     (관리)
 *
 * 호출 API:
 *   - GET    /api/v1/visit/registrant/properties/{propertyId}/slots[?lease_type=]  (현황 조회)
 *   - POST   /api/v1/visit/windows                                                  (윈도우 공개)
 *   - DELETE /api/v1/visit/windows/{windowId}                                       (윈도우 철회)
 *   - PATCH  /api/v1/visit/reservations/{reservationId}/result                      (방문 결과 분류)
 */
(function () {
    var ctx = null;  // { propertyId, leaseType }

    document.addEventListener('DOMContentLoaded', function () {
        ctx = parseQuery();
        if (!ctx.propertyId) {
            document.getElementById('vm_guide').style.display = 'block';
            return;
        }
        document.getElementById('vm_workspace').style.display = 'block';
        document.getElementById('vm_h_property_id').textContent = ctx.propertyId;
        document.getElementById('vm_h_lease_type').textContent =
            ctx.leaseType ? (ctx.leaseType === 'CHARTER' ? '전세' : '월세') : '전체';
        if (ctx.leaseType) document.getElementById('vm_c_lease_type').value = ctx.leaseType;
        loadSlots();
    });

    function parseQuery() {
        var params = new URLSearchParams(window.location.search);
        return {
            propertyId: params.get('propertyId') || params.get('property_id'),
            leaseType: params.get('leaseType') || params.get('lease_type')
        };
    }

    window.__vmGo = function () {
        var pid = document.getElementById('vm_input_property_id').value.trim();
        var lt = document.getElementById('vm_input_lease_type').value;
        if (!pid) { showError('매물 식별자는 필수입니다.'); return; }
        var q = '?propertyId=' + encodeURIComponent(pid);
        if (lt) q += '&leaseType=' + encodeURIComponent(lt);
        window.location.href = '/wherehouse/visit/me/slots' + q;
    };

    // ============================================================
    // 슬롯 현황 조회
    // ============================================================

    function loadSlots() {
        var url = '/wherehouse/api/v1/visit/registrant/properties/' + ctx.propertyId + '/slots';
        if (ctx.leaseType) url += '?lease_type=' + ctx.leaseType;

        fetch(url, { credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 401) { redirectLogin(); throw new Error('LOGIN'); }
                if (res.status === 403) throw new Error('이 매물의 등록자만 조회할 수 있습니다.');
                if (res.status === 404) throw new Error('매물을 찾을 수 없습니다.');
                if (!res.ok) throw new Error('조회 실패: ' + res.status);
                return res.json();
            })
            .then(renderSlots)
            .catch(function (err) {
                if (err.message === 'LOGIN') return;
                showError(err.message);
                document.getElementById('vm_slot_panels').innerHTML =
                    '<div class="vm_empty">슬롯을 불러올 수 없습니다.</div>';
            });
    }

    function renderSlots(data) {
        var panels = document.getElementById('vm_slot_panels');
        var html = '';
        if (data.charter && data.charter.length > 0) html += buildLeasePanel('CHARTER', data.charter);
        if (data.monthly && data.monthly.length > 0) html += buildLeasePanel('MONTHLY', data.monthly);
        if (!html) {
            html = '<div class="vm_empty">공개된 윈도우가 없습니다. "+ 윈도우 공개"로 추가하세요.</div>';
        }
        panels.innerHTML = html;
    }

    function buildLeasePanel(leaseTypeCode, slots) {
        var leaseLabel = leaseTypeCode === 'CHARTER' ? '전세' : '월세';
        var byWindow = {};
        slots.forEach(function (s) {
            if (!byWindow[s.window_id]) byWindow[s.window_id] = [];
            byWindow[s.window_id].push(s);
        });

        var html = '<h3 style="margin-top:20px;font-size:16px;">' + leaseLabel + ' 슬롯</h3>';
        Object.keys(byWindow).forEach(function (wid) {
            var windowSlots = byWindow[wid];
            var anyActive = windowSlots.some(function (s) {
                return s.status === 'AVAILABLE' || s.status === 'RESERVED';
            });
            var withdrawBtn = anyActive
                ? '<button class="vm_btn vm_btn_danger" onclick="window.__vmWithdraw(' + wid + ')">윈도우 철회</button>'
                : '<span class="vm_badge vm_badge_withdrawn">철회/종료된 윈도우</span>';

            html +=
                '<div class="vm_card" style="margin-top:10px;">' +
                    '<div class="vm_card_head">' +
                        '<div class="vm_card_title">윈도우 #' + wid + ' — 슬롯 ' + windowSlots.length + '개</div>' +
                        '<div>' + withdrawBtn + '</div>' +
                    '</div>' +
                    '<div class="vm_card_grid">' + windowSlots.map(buildSlotEntry).join('') + '</div>' +
                '</div>';
        });
        return html;
    }

    function buildSlotEntry(s) {
        var slotStatusBadge = slotStatusBadgeHtml(s.status);
        var reservationHtml = '';
        if (s.reservation) {
            var r = s.reservation;
            var resBadge = reservationStatusBadgeHtml(r.status, r.visit_result);
            var classifyHtml = '';
            // 종료 상태이고 아직 미분류이면 분류 버튼 노출
            if (r.status === 'COMPLETED' && !r.visit_result) {
                classifyHtml =
                    '<div style="margin-top:6px;display:flex;gap:6px;">' +
                        '<button class="vm_btn vm_btn_outline" style="padding:4px 10px;font-size:12px;" ' +
                          'onclick="window.__vmClassify(' + r.reservation_id + ',\'VISITED\')">방문 완료</button>' +
                        '<button class="vm_btn vm_btn_subtle" style="padding:4px 10px;font-size:12px;" ' +
                          'onclick="window.__vmClassify(' + r.reservation_id + ',\'NO_SHOW\')">노쇼</button>' +
                    '</div>';
            }
            var searcherInfo = r.searcher
                ? '<div style="font-size:12px;color:#4b5563;margin-top:4px;">탐색자: ' +
                    escapeHtml(r.searcher.username || '-') +
                    ' / ' + escapeHtml(r.searcher.contact || '-') + '</div>'
                : '';
            reservationHtml =
                '<div style="margin-top:6px;border-top:1px dashed #e5e7eb;padding-top:6px;">' +
                    '<span style="font-size:12px;color:#6b7280;">예약 #' + r.reservation_id + '</span> ' +
                    resBadge +
                    searcherInfo +
                    classifyHtml +
                '</div>';
        }

        return '' +
            '<div style="border:1px solid #e5e7eb;border-radius:8px;padding:10px;">' +
                '<div style="display:flex;justify-content:space-between;align-items:center;">' +
                    '<div style="font-weight:600;font-size:13px;">슬롯 #' + s.slot_id + '</div>' +
                    slotStatusBadge +
                '</div>' +
                '<div style="font-size:12px;color:#4b5563;margin-top:4px;">' +
                    escapeHtml(formatDateTime(s.start_time)) + ' ~ ' + escapeHtml(formatTime(s.end_time)) +
                '</div>' +
                reservationHtml +
            '</div>';
    }

    // ============================================================
    // 윈도우 공개
    // ============================================================

    window.__vmOpenCreate = function () {
        document.getElementById('vm_create_form').style.display = 'block';
    };
    window.__vmCloseCreate = function () {
        document.getElementById('vm_create_form').style.display = 'none';
    };

    window.__vmSubmitCreate = function () {
        var body = {
            property_id: ctx.propertyId,
            lease_type: document.getElementById('vm_c_lease_type').value,
            start_time: document.getElementById('vm_c_start').value + ':00',
            end_time: document.getElementById('vm_c_end').value + ':00',
            slot_duration_minutes: parseInt(document.getElementById('vm_c_duration').value, 10) || 30
        };
        if (!body.start_time || !body.end_time) {
            showError('시작·종료 시각을 모두 입력하세요.');
            return;
        }
        fetch('/wherehouse/api/v1/visit/windows', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
            .then(parseJsonOrThrow)
            .then(function () {
                showSuccess('윈도우를 공개했습니다.');
                window.__vmCloseCreate();
                loadSlots();
            })
            .catch(function (err) { showError(err.message || '공개 실패'); });
    };

    // ============================================================
    // 윈도우 철회
    // ============================================================

    window.__vmWithdraw = function (windowId) {
        if (!confirm('이 윈도우를 철회하시겠습니까? 활성 슬롯의 확정 예약이 모두 무효화되고 영향받은 탐색자에게 통지됩니다. 되돌릴 수 없습니다.')) return;
        fetch('/wherehouse/api/v1/visit/windows/' + windowId, {
            method: 'DELETE',
            credentials: 'same-origin'
        })
            .then(parseJsonOrThrow)
            .then(function (data) {
                var n = (data.invalidated_reservations || []).length;
                showSuccess('윈도우를 철회했습니다. 무효화된 예약: ' + n + '건');
                loadSlots();
            })
            .catch(function (err) { showError(err.message || '철회 실패'); });
    };

    // ============================================================
    // 방문 결과 분류
    // ============================================================

    window.__vmClassify = function (reservationId, result) {
        fetch('/wherehouse/api/v1/visit/reservations/' + reservationId + '/result', {
            method: 'PATCH',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ visit_result: result })
        })
            .then(parseJsonOrThrow)
            .then(function () {
                showSuccess('방문 결과를 ' + (result === 'VISITED' ? '방문 완료' : '노쇼') + '로 분류했습니다.');
                loadSlots();
            })
            .catch(function (err) { showError(err.message || '분류 실패'); });
    };

    // ============================================================
    // 배지 / 포맷 / 유틸
    // ============================================================

    function slotStatusBadgeHtml(s) {
        if (s === 'AVAILABLE') return '<span class="vm_badge vm_badge_available">예약 가능</span>';
        if (s === 'RESERVED')  return '<span class="vm_badge vm_badge_reserved">예약됨</span>';
        if (s === 'CLOSED')    return '<span class="vm_badge vm_badge_closed">종료</span>';
        if (s === 'WITHDRAWN') return '<span class="vm_badge vm_badge_withdrawn">철회</span>';
        return '<span class="vm_badge">' + escapeHtml(s) + '</span>';
    }
    function reservationStatusBadgeHtml(status, visitResult) {
        if (status === 'COMPLETED') {
            if (visitResult === 'VISITED') return '<span class="vm_badge vm_badge_visited">방문 완료</span>';
            if (visitResult === 'NO_SHOW') return '<span class="vm_badge vm_badge_noshow">노쇼</span>';
            return '<span class="vm_badge vm_badge_completed">종료(미분류)</span>';
        }
        if (status === 'CONFIRMED')   return '<span class="vm_badge vm_badge_confirmed">확정</span>';
        if (status === 'CANCELLED')   return '<span class="vm_badge vm_badge_cancelled">취소</span>';
        if (status === 'INVALIDATED') return '<span class="vm_badge vm_badge_invalidated">무효화</span>';
        return '<span class="vm_badge">' + escapeHtml(status) + '</span>';
    }

    function parseJsonOrThrow(res) {
        if (res.status === 401) { redirectLogin(); return Promise.reject(new Error('LOGIN')); }
        return res.json().then(function (body) {
            if (!res.ok) {
                var err = new Error(body.message || ('요청 실패 ' + res.status));
                err.code = body.error_code;
                throw err;
            }
            return body;
        });
    }
    function showSuccess(msg) {
        document.getElementById('vm_alerts').innerHTML =
            '<div class="vm_alert vm_alert_success">' + escapeHtml(msg) + '</div>';
        setTimeout(function () { document.getElementById('vm_alerts').innerHTML = ''; }, 5000);
    }
    function showError(msg) {
        document.getElementById('vm_alerts').innerHTML =
            '<div class="vm_alert vm_alert_error">' + escapeHtml(msg) + '</div>';
    }
    function redirectLogin() {
        if (confirm('로그인이 필요합니다. 로그인 페이지로 이동할까요?'))
            window.location.href = '/wherehouse/members/login';
    }
    function formatDateTime(dt) { return dt ? dt.replace('T', ' ').substring(0, 16) : '-'; }
    function formatTime(dt) { return dt ? dt.substring(11, 16) : '-'; }
    function escapeHtml(t) {
        if (t == null) return '';
        var d = document.createElement('div'); d.textContent = String(t); return d.innerHTML;
    }
})();
