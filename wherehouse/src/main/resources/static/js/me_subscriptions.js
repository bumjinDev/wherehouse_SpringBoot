/**
 * me_subscriptions.js — 탐색자 재개방 알림 구독 현황 페이지.
 *
 * 호출 API: GET /api/v1/visit/searcher/subscriptions  (응답 7.10)
 * 행위: 활성 구독에 한해 해제 버튼 노출
 *      DELETE /api/v1/visit/slots/{slotId}/subscriptions
 */
(function () {
    document.addEventListener('DOMContentLoaded', load);

    function load() {
        fetch('/wherehouse/api/v1/visit/searcher/subscriptions', { credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 401) { redirectLogin(); throw new Error('LOGIN'); }
                if (!res.ok) throw new Error('조회 실패: ' + res.status);
                return res.json();
            })
            .then(render)
            .catch(function (err) { if (err.message !== 'LOGIN') showError(err.message); });
    }

    function render(data) {
        var list = document.getElementById('vm_list');
        var subs = data.subscriptions || [];
        if (subs.length === 0) {
            list.innerHTML = '<div class="vm_empty">구독 중인 슬롯이 없습니다.</div>';
            return;
        }
        list.innerHTML = subs.map(buildCard).join('');
    }

    function buildCard(s) {
        var leaseLabel = s.lease_type === 'CHARTER' ? '전세' : '월세';
        var leaseBadge = s.lease_type === 'CHARTER' ? 'vm_badge_charter' : 'vm_badge_monthly';
        var subStatusBadge = subscriptionStatusBadge(s.status);
        var slotStatusBadge = slotStatusBadgeHtml(s.slot_status);

        var unsubscribeBtn = '';
        if (s.status === 'ACTIVE') {
            unsubscribeBtn = '<button class="vm_btn vm_btn_subtle" onclick="window.__vmUnsub(' +
                s.slot_id + ')">구독 해제</button>';
        }

        return '' +
            '<div class="vm_card">' +
                '<div class="vm_card_head">' +
                    '<div class="vm_card_title">' +
                        '<span class="vm_badge ' + leaseBadge + '">' + leaseLabel + '</span> ' +
                        '슬롯 #' + s.slot_id +
                    '</div>' +
                    '<div>' + subStatusBadge + '</div>' +
                '</div>' +
                '<div class="vm_card_grid">' +
                    '<div class="vm_kv"><b>슬롯 시각</b><span>' + escapeHtml(formatDateTime(s.start_time)) +
                        ' ~ ' + escapeHtml(formatTime(s.end_time)) + '</span></div>' +
                    '<div class="vm_kv"><b>현재 슬롯 상태</b><span>' + slotStatusBadge + '</span></div>' +
                    '<div class="vm_kv"><b>매물 식별자</b><span style="font-family:monospace;font-size:11px;">' +
                        escapeHtml(s.property_id || '-') + '</span></div>' +
                    '<div class="vm_kv"><b>구독 시각</b><span>' + escapeHtml(formatDateTime(s.subscribed_at)) + '</span></div>' +
                '</div>' +
                (unsubscribeBtn ? '<div style="margin-top:12px;text-align:right;">' + unsubscribeBtn + '</div>' : '') +
            '</div>';
    }

    window.__vmUnsub = function (slotId) {
        if (!confirm('이 슬롯의 재개방 알림 구독을 해제하시겠습니까?')) return;
        fetch('/wherehouse/api/v1/visit/slots/' + slotId + '/subscriptions', {
            method: 'DELETE',
            credentials: 'same-origin'
        })
            .then(parseJsonOrThrow)
            .then(function () { showSuccess('구독을 해제했습니다.'); load(); })
            .catch(function (err) { showError(err.message || '해제 실패'); });
    };

    function subscriptionStatusBadge(status) {
        if (status === 'ACTIVE')    return '<span class="vm_badge vm_badge_active">활성</span>';
        if (status === 'FULFILLED') return '<span class="vm_badge vm_badge_fulfilled">재예약 성공</span>';
        if (status === 'CANCELLED') return '<span class="vm_badge vm_badge_cancelled">해제</span>';
        if (status === 'EXPIRED')   return '<span class="vm_badge vm_badge_expired">슬롯 종료</span>';
        return '<span class="vm_badge">' + escapeHtml(status) + '</span>';
    }
    function slotStatusBadgeHtml(s) {
        if (s === 'AVAILABLE') return '<span class="vm_badge vm_badge_available">예약 가능</span>';
        if (s === 'RESERVED')  return '<span class="vm_badge vm_badge_reserved">예약됨</span>';
        if (s === 'CLOSED')    return '<span class="vm_badge vm_badge_closed">종료</span>';
        if (s === 'WITHDRAWN') return '<span class="vm_badge vm_badge_withdrawn">철회</span>';
        return '<span class="vm_badge">' + escapeHtml(s) + '</span>';
    }

    function parseJsonOrThrow(res) {
        if (res.status === 401) { redirectLogin(); return Promise.reject(new Error('LOGIN')); }
        if (res.status === 200) return res.json().catch(function () { return {}; });
        return res.json().then(function (body) {
            if (!res.ok) throw new Error(body.message || ('요청 실패 ' + res.status));
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
