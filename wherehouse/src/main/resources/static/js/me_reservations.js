/**
 * me_reservations.js — 탐색자 예약 현황 페이지.
 *
 * 호출 API: GET /api/v1/visit/searcher/reservations  (F008 응답 7.9)
 * 행위:
 *   - 본인 예약 목록을 최신 확정 순으로 표시
 *   - CONFIRMED 상태이고 슬롯 시작 시각이 미래인 예약은 취소 버튼을 노출
 *     (DELETE /api/v1/visit/reservations/{reservationId})
 */
(function () {
    document.addEventListener('DOMContentLoaded', loadReservations);

    function loadReservations() {
        fetch('/wherehouse/api/v1/visit/searcher/reservations', { credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 401) { redirectLogin(); throw new Error('LOGIN'); }
                if (!res.ok) throw new Error('조회 실패: ' + res.status);
                return res.json();
            })
            .then(render)
            .catch(function (err) {
                if (err.message === 'LOGIN') return;
                showError(err.message);
            });
    }

    function render(data) {
        var list = document.getElementById('vm_list');
        var reservations = data.reservations || [];
        if (reservations.length === 0) {
            list.innerHTML = '<div class="vm_empty">아직 예약한 방문이 없습니다.</div>';
            return;
        }
        var now = new Date();
        list.innerHTML = reservations.map(function (r) { return buildCard(r, now); }).join('');
    }

    function buildCard(r, now) {
        var leaseLabel = r.lease_type === 'CHARTER' ? '전세' : '월세';
        var leaseBadge = r.lease_type === 'CHARTER' ? 'vm_badge_charter' : 'vm_badge_monthly';
        var statusBadge = statusBadgeHtml(r.status, r.visit_result);

        var cancellable = false;
        try {
            cancellable = r.status === 'CONFIRMED'
                && r.start_time && new Date(r.start_time) > now;
        } catch (e) { cancellable = false; }

        var registrantInfo = '';
        if (r.registrant) {
            registrantInfo =
                '<div class="vm_kv"><b>등록자</b><span>' + escapeHtml(r.registrant.username || '-') + '</span></div>' +
                '<div class="vm_kv"><b>등록자 연락처</b><span>' + escapeHtml(r.registrant.contact || '-') + '</span></div>';
        }

        var cancelBtn = cancellable
            ? '<button class="vm_btn vm_btn_danger" onclick="window.__vmCancel(' + r.reservation_id + ')">예약 취소</button>'
            : '';

        return '' +
            '<div class="vm_card" id="vm_res_' + r.reservation_id + '">' +
                '<div class="vm_card_head">' +
                    '<div class="vm_card_title">' +
                        '<span class="vm_badge ' + leaseBadge + '">' + leaseLabel + '</span> ' +
                        '예약 #' + r.reservation_id +
                    '</div>' +
                    '<div>' + statusBadge + '</div>' +
                '</div>' +
                '<div class="vm_card_grid">' +
                    '<div class="vm_kv"><b>방문 시각</b><span>' + escapeHtml(formatDateTime(r.start_time)) +
                        ' ~ ' + escapeHtml(formatTime(r.end_time)) + '</span></div>' +
                    '<div class="vm_kv"><b>매물 식별자</b><span style="font-family:monospace;font-size:11px;">' +
                        escapeHtml(r.property_id || '-') + '</span></div>' +
                    '<div class="vm_kv"><b>확정 시각</b><span>' + escapeHtml(formatDateTime(r.confirmed_at)) + '</span></div>' +
                    registrantInfo +
                '</div>' +
                (cancelBtn ? '<div style="margin-top:12px;text-align:right;">' + cancelBtn + '</div>' : '') +
            '</div>';
    }

    window.__vmCancel = function (reservationId) {
        if (!confirm('이 예약을 취소하시겠습니까? 슬롯이 다시 다른 탐색자에게 공개됩니다.')) return;
        fetch('/wherehouse/api/v1/visit/reservations/' + reservationId, {
            method: 'DELETE',
            credentials: 'same-origin'
        })
            .then(parseJsonOrThrow)
            .then(function () {
                showSuccess('예약이 취소되었습니다.');
                loadReservations();
            })
            .catch(function (err) {
                showError(err.message || '취소 실패');
            });
    };

    function statusBadgeHtml(status, visitResult) {
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
