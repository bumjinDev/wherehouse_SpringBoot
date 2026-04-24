/**
 * property_register.js — F001 매물 등록 폼 처리.
 *
 * 전역 변수 currentUserId 는 JSP 에서 주입됨 (null 이면 비인증).
 * Jackson SNAKE_CASE 전략에 의해 API 요청 body 키는 snake_case.
 */

document.addEventListener('DOMContentLoaded', function() {
    if (!currentUserId) {
        document.getElementById('register_form').style.display = 'none';
        document.getElementById('register_auth_notice').style.display = 'block';
    }
});

function toggleMonthlyRent() {
    var leaseType = document.querySelector('input[name="lease_type"]:checked').value;
    var group = document.getElementById('reg_monthly_rent_group');
    var label = document.getElementById('deposit_label');

    if (leaseType === 'MONTHLY') {
        group.style.display = 'flex';
        label.innerHTML = '보증금 (만원) <span class="pb_required">*</span>';
    } else {
        group.style.display = 'none';
        label.innerHTML = '전세금 (만원) <span class="pb_required">*</span>';
        document.getElementById('reg_monthly_rent').value = '';
    }
}

function submitRegister() {
    var errorDiv = document.getElementById('register_error');
    errorDiv.style.display = 'none';

    var leaseType = document.querySelector('input[name="lease_type"]:checked').value;
    var apiPath = leaseType === 'CHARTER' ? 'charter' : 'monthly';

    // 필수값 검증
    var aptNm = document.getElementById('reg_apt_nm').value.trim();
    var sggCd = document.getElementById('reg_sgg_cd').value;
    var umdNm = document.getElementById('reg_umd_nm').value.trim();
    var jibun = document.getElementById('reg_jibun').value.trim();
    var floor = document.getElementById('reg_floor').value;
    var excluUseAr = document.getElementById('reg_exclu_use_ar').value;
    var deposit = document.getElementById('reg_deposit').value;

    if (!aptNm || !sggCd || !umdNm || !jibun || !floor || !excluUseAr || !deposit) {
        showError('필수 입력 항목을 모두 채워주세요.');
        return;
    }

    if (leaseType === 'MONTHLY') {
        var monthlyRent = document.getElementById('reg_monthly_rent').value;
        if (!monthlyRent) {
            showError('월세금은 필수입니다.');
            return;
        }
    }

    // 요청 본문 구성 (snake_case)
    var body = {
        apt_nm: aptNm,
        sgg_cd: sggCd,
        umd_nm: umdNm,
        jibun: jibun,
        floor: parseInt(floor),
        exclu_use_ar: parseFloat(excluUseAr),
        deposit: parseInt(deposit)
    };

    var buildYear = document.getElementById('reg_build_year').value;
    if (buildYear) body.build_year = parseInt(buildYear);

    var dealDate = document.getElementById('reg_deal_date').value;
    if (dealDate) body.deal_date = dealDate;

    if (leaseType === 'MONTHLY') {
        body.monthly_rent = parseInt(document.getElementById('reg_monthly_rent').value);
    }

    fetch('/wherehouse/api/v1/properties/' + apiPath, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    })
    .then(function(res) {
        if (res.status === 401) throw new Error('로그인이 필요합니다. 로그인 후 다시 시도해주세요.');
        if (res.status === 409) throw new Error('동일한 매물이 이미 등록되어 있습니다.');
        if (!res.ok) return res.json().then(function(e) { throw new Error(e.message || '등록 실패'); });
        return res.json();
    })
    .then(function(data) {
        alert('매물이 등록되었습니다.\n매물 ID: ' + data.property_id);
        location.href = '/wherehouse/properties/board';
    })
    .catch(function(err) {
        showError(err.message);
    });
}

function showError(msg) {
    var errorDiv = document.getElementById('register_error');
    errorDiv.textContent = msg;
    errorDiv.style.display = 'block';
}
