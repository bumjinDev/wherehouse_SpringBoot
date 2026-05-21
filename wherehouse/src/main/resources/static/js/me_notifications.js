/**
 * me_notifications.js — 방문 예약 알림 목록 페이지.
 *
 * 호출 API:
 *   - GET   /api/v1/visit/notifications?unread_only={bool}&limit=20&before={cursor}
 *   - PATCH /api/v1/visit/notifications/{notificationId}/read
 *
 * 응답의 next_before 가 null 이면 추가 페이지 없음.
 */
(function () {
    var unreadOnly = false;
    var nextBefore = null;
    var loadedCount = 0;

    document.addEventListener('DOMContentLoaded', function () { reload(); });

    window.__vmReload = function () {
        unreadOnly = document.getElementById('vm_unread_only').checked;
        reload();
    };
    window.__vmLoadMore = function () {
        if (nextBefore == null) return;
        fetchPage(nextBefore);
    };

    function reload() {
        nextBefore = null;
        loadedCount = 0;
        document.getElementById('vm_list').innerHTML = '<div class="vm_empty">불러오는 중...</div>';
        document.getElementById('vm_load_more').style.display = 'none';
        fetchPage(null);
    }

    function fetchPage(beforeCursor) {
        var url = '/wherehouse/api/v1/visit/notifications?limit=20';
        if (unreadOnly) url += '&unread_only=true';
        if (beforeCursor != null) url += '&before=' + beforeCursor;

        fetch(url, { credentials: 'same-origin' })
            .then(function (res) {
                if (res.status === 401) { redirectLogin(); throw new Error('LOGIN'); }
                if (!res.ok) throw new Error('조회 실패: ' + res.status);
                return res.json();
            })
            .then(function (data) { appendPage(data); })
            .catch(function (err) { if (err.message !== 'LOGIN') showError(err.message); });
    }

    function appendPage(data) {
        var list = document.getElementById('vm_list');
        var items = data.notifications || [];

        if (loadedCount === 0) {
            list.innerHTML = items.length === 0
                ? '<div class="vm_empty">알림이 없습니다.</div>'
                : '';
        }

        items.forEach(function (n) { list.insertAdjacentHTML('beforeend', buildCard(n)); });
        loadedCount += items.length;

        nextBefore = data.next_before;
        document.getElementById('vm_load_more').style.display = nextBefore == null ? 'none' : 'inline-block';
    }

    function buildCard(n) {
        var readBadge = n.is_read
            ? '<span class="vm_badge vm_badge_read">읽음</span>'
            : '<span class="vm_badge vm_badge_unread">미읽음</span>';

        var typeLabel = formatType(n.notification_type);
        var readBtn = !n.is_read
            ? '<button class="vm_btn vm_btn_subtle" onclick="window.__vmMarkRead(' + n.notification_id + ')">읽음 처리</button>'
            : '';

        var relatedHtml = '';
        if (n.related_property_id) {
            relatedHtml += '<div class="vm_kv"><b>관련 매물</b><span style="font-family:monospace;font-size:11px;">' +
                escapeHtml(n.related_property_id) + '</span></div>';
        }
        if (n.related_slot_id) {
            relatedHtml += '<div class="vm_kv"><b>관련 슬롯</b><span>#' + n.related_slot_id + '</span></div>';
        }
        if (n.related_reservation_id) {
            relatedHtml += '<div class="vm_kv"><b>관련 예약</b><span>#' + n.related_reservation_id + '</span></div>';
        }

        return '' +
            '<div class="vm_card" id="vm_noti_' + n.notification_id + '">' +
                '<div class="vm_card_head">' +
                    '<div class="vm_card_title">' + escapeHtml(typeLabel) + '</div>' +
                    '<div>' + readBadge + '</div>' +
                '</div>' +
                '<div style="font-size:14px;color:#1f2937;margin-bottom:10px;">' + escapeHtml(n.message || '') + '</div>' +
                '<div class="vm_card_grid">' +
                    '<div class="vm_kv"><b>알림 ID</b><span>#' + n.notification_id + '</span></div>' +
                    '<div class="vm_kv"><b>생성 시각</b><span>' + escapeHtml(formatDateTime(n.created_at)) + '</span></div>' +
                    relatedHtml +
                '</div>' +
                (readBtn ? '<div style="margin-top:10px;text-align:right;">' + readBtn + '</div>' : '') +
            '</div>';
    }

    window.__vmMarkRead = function (notificationId) {
        fetch('/wherehouse/api/v1/visit/notifications/' + notificationId + '/read', {
            method: 'PATCH',
            credentials: 'same-origin'
        })
            .then(function (res) {
                if (res.status === 401) { redirectLogin(); throw new Error('LOGIN'); }
                if (!res.ok) throw new Error('읽음 처리 실패');
                return res.json();
            })
            .then(function () { reload(); })
            .catch(function (err) { if (err.message !== 'LOGIN') showError(err.message); });
    };

    function formatType(t) {
        if (t === 'SLOT_RESERVED')           return '슬롯 예약 확정';
        if (t === 'RESERVATION_INVALIDATED') return '예약 무효화';
        if (t === 'SLOT_REOPENED')           return '슬롯 재개방';
        if (t === 'PROPERTY_DEACTIVATED')    return '매물 비활성화';
        return t || '알림';
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
    function escapeHtml(t) {
        if (t == null) return '';
        var d = document.createElement('div'); d.textContent = String(t); return d.innerHTML;
    }
})();
