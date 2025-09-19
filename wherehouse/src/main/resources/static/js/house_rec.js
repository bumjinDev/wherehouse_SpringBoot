/**
 * WhereHouse 부동산 추천 시스템 - 메인 JavaScript (수정 버전)
 * 사용자 입력 처리 및 추천 결과 표시
 * 전세/월세 분리 API 대응
 */

// 전역 변수
let currentRecommendationData = null;
let currentRentalType = null;

// DOM 로드 완료 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM 로드 완료, 이벤트 리스너 초기화 시작');
    initializeEventListeners();
    initializeValidation();
    updateSubmitButtonState();
    setupModalEventListeners();
});

/**
 * 이벤트 리스너 초기화
 */
function initializeEventListeners() {
    console.log('이벤트 리스너 초기화 중...');

    // 1단계: 기본 조건 설정
    setupRentalTypeHandlers();
    setupBudgetValidation();
    setupAreaValidation();

    // 2단계: 우선순위 설정
    setupPriorityValidation();

    // 3단계: 유연성 설정
    setupFlexibilityHandlers();

    // 추천 결과 확인 버튼
    setupSubmitButton();

    // 뒤로가기 버튼
    setupBackButton();

    console.log('모든 이벤트 리스너 초기화 완료');
}

/**
 * 임대 유형 선택 핸들러
 */
function setupRentalTypeHandlers() {
    const rentalTypeRadios = document.querySelectorAll('input[name="rentalType"]');
    const monthlyExtraFields = document.getElementById('monthlyExtraFields');

    console.log('임대 유형 라디오 버튼 개수:', rentalTypeRadios.length);

    rentalTypeRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            console.log('임대 유형 변경:', this.value);
            currentRentalType = this.value;

            if (this.value === 'MONTHLY') {
                monthlyExtraFields.classList.add('show');
            } else {
                monthlyExtraFields.classList.remove('show');
                // 월세 필드 초기화
                document.getElementById('monthlyMin').value = '';
                document.getElementById('monthlyMax').value = '';
            }
            updateSubmitButtonState();
        });
    });
}

/**
 * 예산 범위 검증 설정
 */
function setupBudgetValidation() {
    const budgetMin = document.getElementById('budgetMin');
    const budgetMax = document.getElementById('budgetMax');
    const monthlyMin = document.getElementById('monthlyMin');
    const monthlyMax = document.getElementById('monthlyMax');
    const budgetError = document.getElementById('budgetError');

    function validateBudget() {
        const minVal = parseInt(budgetMin.value);
        const maxVal = parseInt(budgetMax.value);

        if (budgetMin.value && budgetMax.value) {
            if (minVal > maxVal) {
                showError(budgetError, '최대 예산이 최소 예산보다 크거나 같아야 합니다');
                return false;
            }
        }

        hideError(budgetError);
        return true;
    }

    function validateMonthly() {
        const rentalType = document.querySelector('input[name="rentalType"]:checked')?.value;
        if (rentalType !== 'MONTHLY') return true;

        const minVal = parseInt(monthlyMin.value);
        const maxVal = parseInt(monthlyMax.value);

        if (monthlyMin.value && monthlyMax.value) {
            if (minVal > maxVal) {
                showError(budgetError, '최대 월세가 최소 월세보다 크거나 같아야 합니다');
                return false;
            }
        }

        return true;
    }

    [budgetMin, budgetMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateBudget();
                updateSubmitButtonState();
            });
        }
    });

    [monthlyMin, monthlyMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateMonthly();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 평수 범위 검증 설정
 */
function setupAreaValidation() {
    const areaMin = document.getElementById('areaMin');
    const areaMax = document.getElementById('areaMax');
    const areaError = document.getElementById('areaError');

    function validateArea() {
        const minVal = parseFloat(areaMin.value);
        const maxVal = parseFloat(areaMax.value);

        if (areaMin.value && areaMax.value) {
            if (minVal > maxVal) {
                showError(areaError, '최대 평수가 최소 평수보다 크거나 같아야 합니다');
                return false;
            }
        }

        hideError(areaError);
        return true;
    }

    [areaMin, areaMax].forEach(input => {
        if (input) {
            input.addEventListener('input', () => {
                validateArea();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 우선순위 선택 검증 설정
 */
function setupPriorityValidation() {
    const priority1 = document.getElementById('priority1');
    const priority2 = document.getElementById('priority2');
    const priority3 = document.getElementById('priority3');
    const priorityError = document.getElementById('priorityError');

    function validatePriorities() {
        const p1 = priority1.value;
        const p2 = priority2.value;
        const p3 = priority3.value;

        if (p1 && p2 && p3) {
            const values = [p1, p2, p3];
            const uniqueValues = [...new Set(values)];

            if (uniqueValues.length !== 3) {
                showError(priorityError, '우선순위는 중복될 수 없습니다');
                return false;
            }
        }

        hideError(priorityError);
        return true;
    }

    [priority1, priority2, priority3].forEach(select => {
        if (select) {
            select.addEventListener('change', () => {
                validatePriorities();
                updateSubmitButtonState();
            });
        }
    });
}

/**
 * 유연성 설정 핸들러
 */
function setupFlexibilityHandlers() {
    // 예산 유연성 슬라이더
    const budgetFlexSlider = document.getElementById('budgetFlexibility');
    const budgetFlexValue = document.getElementById('budgetFlexValue');

    if (budgetFlexSlider && budgetFlexValue) {
        budgetFlexSlider.addEventListener('input', function() {
            budgetFlexValue.textContent = this.value + '%';
        });
    }

    // 최소 안전 점수 슬라이더
    const safetyScoreSlider = document.getElementById('minSafetyScore');
    const safetyScoreValue = document.getElementById('safetyScoreValue');

    if (safetyScoreSlider && safetyScoreValue) {
        safetyScoreSlider.addEventListener('input', function() {
            safetyScoreValue.textContent = this.value + '점';
        });
    }
}

/**
 * 추천 결과 확인 버튼 설정
 */
function setupSubmitButton() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    console.log('제출 버튼 찾기 결과:', submitButton);

    if (submitButton) {
        submitButton.removeEventListener('click', handleSubmitClick);
        submitButton.addEventListener('click', handleSubmitClick);
        console.log('제출 버튼 이벤트 리스너 설정 완료');
    } else {
        console.error('제출 버튼을 찾을 수 없습니다!');
    }
}

/**
 * 제출 버튼 클릭 핸들러
 */
function handleSubmitClick(event) {
    console.log('제출 버튼 클릭됨');
    event.preventDefault();

    const button = event.target;

    if (button.disabled) {
        console.log('버튼이 비활성화 상태입니다');
        return;
    }

    console.log('폼 검증 시작...');
    if (validateAllFields()) {
        console.log('폼 검증 성공, API 요청 시작');
        submitRecommendationRequest();
    } else {
        console.log('폼 검증 실패');
    }
}

/**
 * 뒤로가기 버튼 설정
 */
function setupBackButton() {
    // 전세/월세 결과 페이지의 뒤로가기 버튼들
    const charterBackButton = document.querySelector('#charter_result_title p');
    const monthlyBackButton = document.querySelector('#monthly_result_title p');

    if (charterBackButton) {
        charterBackButton.addEventListener('click', function() {
            showInputPage();
        });
    }

    if (monthlyBackButton) {
        monthlyBackButton.addEventListener('click', function() {
            showInputPage();
        });
    }
}

/**
 * 모든 필드 검증
 */
function validateAllFields() {
    console.log('=== 전체 필드 검증 시작 ===');

    // 1. 임대 유형 검증
    const rentalType = document.querySelector('input[name="rentalType"]:checked')?.value;
    console.log('임대 유형:', rentalType);
    if (!rentalType) {
        alert('임대 유형을 선택해주세요.');
        return false;
    }

    // 2. 예산 범위 검증
    const budgetMin = document.getElementById('budgetMin').value;
    const budgetMax = document.getElementById('budgetMax').value;
    console.log('예산 범위:', budgetMin, '~', budgetMax);

    if (!budgetMin || !budgetMax) {
        alert('예산 범위를 모두 입력해주세요.');
        return false;
    }

    if (parseInt(budgetMin) > parseInt(budgetMax)) {
        alert('최대 예산이 최소 예산보다 크거나 같아야 합니다.');
        return false;
    }

    // 3. 월세 추가 필드 검증 (월세 선택 시)
    if (rentalType === 'MONTHLY') {
        const monthlyMin = document.getElementById('monthlyMin').value;
        const monthlyMax = document.getElementById('monthlyMax').value;
        console.log('월세 범위:', monthlyMin, '~', monthlyMax);

        if (!monthlyMin || !monthlyMax) {
            alert('월세 범위를 입력해주세요.');
            return false;
        }

        if (parseInt(monthlyMin) > parseInt(monthlyMax)) {
            alert('최대 월세가 최소 월세보다 크거나 같아야 합니다.');
            return false;
        }
    }

    // 4. 평수 범위 검증
    const areaMin = document.getElementById('areaMin').value;
    const areaMax = document.getElementById('areaMax').value;
    console.log('평수 범위:', areaMin, '~', areaMax);

    if (!areaMin || !areaMax) {
        alert('평수 범위를 모두 입력해주세요.');
        return false;
    }

    if (parseFloat(areaMin) > parseFloat(areaMax)) {
        alert('최대 평수가 최소 평수보다 크거나 같아야 합니다.');
        return false;
    }

    // 5. 우선순위 검증
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    console.log('우선순위:', priority1, priority2, priority3);

    if (!priority1 || !priority2 || !priority3) {
        alert('모든 우선순위를 설정해주세요.');
        return false;
    }

    // 우선순위 중복 검증
    const priorities = [priority1, priority2, priority3];
    const uniquePriorities = [...new Set(priorities)];
    if (uniquePriorities.length !== 3) {
        alert('우선순위는 중복될 수 없습니다.');
        return false;
    }

    console.log('=== 전체 필드 검증 성공 ===');
    return true;
}

/**
 * 제출 버튼 상태 업데이트
 */
function updateSubmitButtonState() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (!submitButton) {
        console.warn('제출 버튼을 찾을 수 없어 상태 업데이트를 건너뜁니다.');
        return;
    }

    const isValid = isFormValid();

    submitButton.disabled = !isValid;

    if (isValid) {
        submitButton.style.backgroundColor = '#0B5ED7';
        submitButton.style.cursor = 'pointer';
        submitButton.style.color = 'white';
    } else {
        submitButton.style.backgroundColor = '#ccc';
        submitButton.style.cursor = 'not-allowed';
        submitButton.style.color = '#666';
    }
}

/**
 * 폼 유효성 확인
 */
function isFormValid() {
    const rentalType = document.querySelector('input[name="rentalType"]:checked');
    const budgetMin = document.getElementById('budgetMin').value;
    const budgetMax = document.getElementById('budgetMax').value;
    const areaMin = document.getElementById('areaMin').value;
    const areaMax = document.getElementById('areaMax').value;
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;

    let isValid = rentalType && budgetMin && budgetMax && areaMin && areaMax &&
        priority1 && priority2 && priority3;

    // 월세 선택 시 추가 검증
    if (rentalType?.value === 'MONTHLY') {
        const monthlyMin = document.getElementById('monthlyMin').value;
        const monthlyMax = document.getElementById('monthlyMax').value;
        isValid = isValid && monthlyMin && monthlyMax;
    }

    // 범위 검증
    if (isValid) {
        isValid = parseInt(budgetMax) >= parseInt(budgetMin) &&
            parseFloat(areaMax) >= parseFloat(areaMin);
    }

    // 우선순위 중복 검증
    if (isValid && priority1 && priority2 && priority3) {
        const priorities = [priority1, priority2, priority3];
        const uniquePriorities = [...new Set(priorities)];
        isValid = uniquePriorities.length === 3;
    }

    return isValid;
}

/**
 * 추천 요청 제출 - 전세/월세 분리 API 대응
 */
/**
 * 추천 요청 제출 - 전세/월세 분리 API 대응
 */
async function submitRecommendationRequest() {
    try {
        console.log('=== API 요청 시작 ===');

        // 로딩 상태 표시
        showLoading();

        // 임대 유형 확인
        const rentalType = document.querySelector('input[name="rentalType"]:checked').value;
        console.log('선택된 임대 유형:', rentalType);

        // 요청 데이터 구성 및 API 엔드포인트 결정
        let requestData, apiEndpoint;

        if (rentalType === 'CHARTER') {
            // 전세용 요청 데이터 및 엔드포인트
            requestData = buildCharterRequestData();
            apiEndpoint = '/wherehouse/api/recommendations/charter-districts';  // ← 수정
        } else if (rentalType === 'MONTHLY') {
            // 월세용 요청 데이터 및 엔드포인트
            requestData = buildMonthlyRequestData();
            apiEndpoint = '/wherehouse/api/recommendations/monthly-districts';  // ← 수정
        } else {
            throw new Error('올바르지 않은 임대 유형입니다.');
        }

        console.log('API 엔드포인트:', apiEndpoint);
        console.log('전송할 데이터:', requestData);

        // API 요청
        const response = await fetch(apiEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        console.log('응답 상태:', response.status);

        if (!response.ok) {
            if (response.status === 400) {
                const errorData = await response.json();
                throw new Error(errorData.message || '입력 데이터에 오류가 있습니다.');
            }
            throw new Error(`서버 오류가 발생했습니다. (${response.status})`);
        }

        const responseData = await response.json();
        console.log('서버 응답:', responseData);

        // 결과 저장 및 화면 표시
        currentRecommendationData = responseData;
        currentRentalType = rentalType;
        showRecommendationResults(responseData, rentalType);

    } catch (error) {
        console.error('추천 요청 오류:', error);
        alert(error.message || '추천 결과를 가져오는 중 오류가 발생했습니다.\n\n네트워크 연결과 서버 상태를 확인해주세요.');
    } finally {
        hideLoading();
    }
}

/**
 * 전세용 요청 데이터 구성
 */
function buildCharterRequestData() {
    const budgetMin = parseInt(document.getElementById('budgetMin').value);
    const budgetMax = parseInt(document.getElementById('budgetMax').value);
    const areaMin = parseFloat(document.getElementById('areaMin').value);
    const areaMax = parseFloat(document.getElementById('areaMax').value);
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    const budgetFlexibility = parseInt(document.getElementById('budgetFlexibility').value);
    const minSafetyScore = parseInt(document.getElementById('minSafetyScore').value);
    const absoluteMinArea = parseFloat(document.getElementById('absoluteMinArea').value) || 0.0;

    return {
        budgetMin: budgetMin,           // 전세금 최소
        budgetMax: budgetMax,           // 전세금 최대
        areaMin: areaMin,
        areaMax: areaMax,
        priority1: priority1,
        priority2: priority2,
        priority3: priority3,
        budgetFlexibility: budgetFlexibility,
        minSafetyScore: minSafetyScore,
        absoluteMinArea: absoluteMinArea
    };
}

/**
 * 월세용 요청 데이터 구성
 */
function buildMonthlyRequestData() {
    const budgetMin = parseInt(document.getElementById('budgetMin').value);        // 보증금 최소
    const budgetMax = parseInt(document.getElementById('budgetMax').value);        // 보증금 최대
    const monthlyRentMin = parseInt(document.getElementById('monthlyMin').value);  // 월세금 최소
    const monthlyRentMax = parseInt(document.getElementById('monthlyMax').value);  // 월세금 최대
    const areaMin = parseFloat(document.getElementById('areaMin').value);
    const areaMax = parseFloat(document.getElementById('areaMax').value);
    const priority1 = document.getElementById('priority1').value;
    const priority2 = document.getElementById('priority2').value;
    const priority3 = document.getElementById('priority3').value;
    const budgetFlexibility = parseInt(document.getElementById('budgetFlexibility').value);
    const minSafetyScore = parseInt(document.getElementById('minSafetyScore').value);
    const absoluteMinArea = parseFloat(document.getElementById('absoluteMinArea').value) || 0.0;

    return {
        budgetMin: budgetMin,                 // 보증금 최소
        budgetMax: budgetMax,                 // 보증금 최대
        monthlyRentMin: monthlyRentMin,       // 월세금 최소 (새로 추가된 필드)
        monthlyRentMax: monthlyRentMax,       // 월세금 최대 (새로 추가된 필드)
        areaMin: areaMin,
        areaMax: areaMax,
        priority1: priority1,
        priority2: priority2,
        priority3: priority3,
        budgetFlexibility: budgetFlexibility,
        minSafetyScore: minSafetyScore,
        absoluteMinArea: absoluteMinArea
    };
}

/**
 * 추천 결과 표시 (전세/월세 분기 처리)
 */
function showRecommendationResults(data, rentalType) {
    console.log('추천 결과 표시:', data);
    console.log('임대 유형:', rentalType);

    if (rentalType === 'CHARTER') {
        showCharterResults(data);
    } else if (rentalType === 'MONTHLY') {
        showMonthlyResults(data);
    }
}

/**
 * 전세 결과 표시 - 수정된 버전
 */
function showCharterResults(data) {
    console.log('전세 결과 표시');
    console.log('응답 데이터 구조:', data);

    // 입력 화면 숨기기, 전세 결과 화면 표시
    document.getElementById('user-input').style.display = 'none';
    document.getElementById('charter_result_page').style.display = 'block';
    document.getElementById('monthly_result_page').style.display = 'none';

    // 안내 메시지 업데이트 - searchStatus에 따라 메시지 결정
    const messageElement = document.getElementById('charter_message_text');
    if (messageElement) {
        console.log('data.searchStatus', data.searchStatus);

        if (data.searchStatus === 'NO_RESULTS') {
            // NO_RESULTS일 때는 무조건 "찾을 수 없었습니다" 메시지
            messageElement.textContent = '조건에 맞는 전세 매물을 찾을 수 없었습니다.';
            console.log('NO_RESULTS - 결과 없음 메시지 설정');
        } else if (data.recommendedDistricts && data.recommendedDistricts.length > 0) {
            // 실제로 추천 결과가 있을 때만 "성공적으로 찾았습니다" 메시지
            messageElement.textContent = '조건에 맞는 전세 매물을 성공적으로 찾았습니다.';
            console.log('SUCCESS - 성공 메시지 설정');
        } else {
            // 기타 경우
            messageElement.textContent = '전세 추천 결과가 없습니다.';
            console.log('OTHER - 기타 메시지 설정');
        }
    }

    // 전세 지역구 카드 생성
    renderCharterDistrictCards(data.recommendedDistricts);
}

/**
 * 월세 결과 표시 - 수정된 버전
 */
function showMonthlyResults(data) {
    console.log('월세 결과 표시');

    // 입력 화면 숨기기, 월세 결과 화면 표시
    document.getElementById('user-input').style.display = 'none';
    document.getElementById('charter_result_page').style.display = 'none';
    document.getElementById('monthly_result_page').style.display = 'block';

    const messageElement = document.getElementById('monthly_message_text');
    if (messageElement) {
        if (data.searchStatus === 'NO_RESULTS') {
            messageElement.textContent = '조건에 맞는 월세 매물을 찾을 수 없었습니다.';
        } else if (data.recommendedDistricts && data.recommendedDistricts.length > 0) {
            messageElement.textContent = '조건에 맞는 월세 매물을 성공적으로 찾았습니다.';
        } else {
            messageElement.textContent = '월세 추천 결과가 없습니다.';
        }
    }

    renderMonthlyDistrictCards(data.recommendedDistricts);
}

/**
 * 전세 지역구 카드 렌더링
 */
function renderCharterDistrictCards(districts) {
    const container = document.getElementById('charter_districts_container');
    if (!container) {
        console.error('전세 지역구 컨테이너를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!districts || districts.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px;">전세 추천 결과가 없습니다.</div>';
        return;
    }

    districts.forEach((district, index) => {
        const card = createCharterDistrictCard(district, index + 1);
        container.appendChild(card);
    });
}

/**
 * 월세 지역구 카드 렌더링
 */
function renderMonthlyDistrictCards(districts) {
    const container = document.getElementById('monthly_districts_container');
    if (!container) {
        console.error('월세 지역구 컨테이너를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!districts || districts.length === 0) {
        container.innerHTML = '<div style="text-align: center; padding: 20px;">월세 추천 결과가 없습니다.</div>';
        return;
    }

    districts.forEach((district, index) => {
        const card = createMonthlyDistrictCard(district, index + 1);
        container.appendChild(card);
    });
}

/**
 * 전세 지역구 카드 생성 - 실제 응답 구조에 맞게 수정
 */
function createCharterDistrictCard(district, rank) {
    const card = document.createElement('div');
    card.className = 'charter_district_card';
    card.id = `charter_district_card_${rank}`;

    // 실제 응답 필드명 사용 (스네이크 케이스)
    const topProperty = district.top_properties && district.top_properties.length > 0
        ? district.top_properties[0] : null;

    const priceText = topProperty ? formatCharterPrice(topProperty) : '매물 정보 없음';
    const scoreText = topProperty ? `${topProperty.final_score.toFixed(1)}점` : '-';

    card.innerHTML = `
        <div class="charter_district_header">
            <span class="charter_district_rank">${rank}.</span>
            <span class="charter_district_name">${district.district_name}</span>
        </div>
        
        <div class="charter_district_info">
            <div class="charter_info_row">
                <span class="charter_info_label">대표 매물:</span>
                <span class="charter_info_value charter_price_value">${priceText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 점수:</span>
                <span class="charter_info_value charter_score_value">${scoreText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 근거:</span>
                <span class="charter_info_value">${district.summary || '정보 없음'}</span>
            </div>
        </div>
        
        <div class="charter_district_buttons">
            <button class="charter_btn_detail_rank" onclick="showPropertyModal('${district.district_name}', 'charter', ${rank})">상세 매물들 보기</button>
            <button class="charter_btn_property_list">지역구 추천 정보</button>
        </div>
    `;

    return card;
}


/**
 * 전세 가격 형식화
 */
function formatCharterPrice(property) {
    return `전세 ${property.price.toLocaleString()}만원`;
}

/**
 * 월세 지역구 카드 생성 - 상세 순위 정보 버튼 기능 추가
 */
function createMonthlyDistrictCard(district, rank) {
    const card = document.createElement('div');
    card.className = 'monthly_district_card';
    card.id = `monthly_district_card_${rank}`;

    const topProperty = district.top_properties && district.top_properties.length > 0
        ? district.top_properties[0] : null;

    const priceText = topProperty ? formatMonthlyPrice(topProperty) : '매물 정보 없음';
    const scoreText = topProperty ? `${topProperty.final_score.toFixed(1)}점` : '-';

    card.innerHTML = `
        <div class="monthly_district_header">
            <span class="monthly_district_rank">${rank}.</span>
            <span class="monthly_district_name">${district.district_name}</span>
        </div>
        
        <div class="monthly_district_info">
            <div class="monthly_info_row">
                <span class="monthly_info_label">대표 매물:</span>
                <span class="monthly_info_value monthly_price_value">${priceText}</span>
            </div>
            <div class="monthly_info_row">
                <span class="monthly_info_label">추천 점수:</span>
                <span class="monthly_info_value monthly_score_value">${scoreText}</span>
            </div>
            <div class="monthly_info_row">
                <span class="monthly_info_label">추천 근거:</span>
                <span class="monthly_info_value">${district.summary || '정보 없음'}</span>
            </div>
        </div>
        
        <div class="monthly_district_buttons">
            <button class="monthly_btn_detail_rank" onclick="showDetailRankModal('${district.district_name}', 'monthly', ${rank})">지역구 추천 정보</button>
            <button class="monthly_btn_property_list" onclick="showMonthlyPropertyListModal('${district.district_name}', ${rank})">상세 매물들 보기</button>
        </div>
    `;

    return card;
}

/**
 * 입력 페이지 표시
 */
function showInputPage() {
    document.getElementById('user-input').style.display = 'block';
    document.getElementById('charter_result_page').style.display = 'none';
    document.getElementById('monthly_result_page').style.display = 'none';
}

/**
 * 월세 가격 형식화 - 보증금과 월세금 모두 표시
 */
function formatMonthlyPrice(property) {
    // snake_case 필드명 사용
    if (property.monthly_rent) {
        return `보증금 ${property.price.toLocaleString()}만원 / 월세 ${property.monthly_rent.toLocaleString()}만원`;
    } else {
        return `보증금 ${property.price.toLocaleString()}만원`;
    }
}

/**
 * 매물 상세 모달 표시 - 새로운 간단한 버전
 */
function showPropertyModal(districtName, rentalType, rank) {
    console.log(`매물 상세 모달 표시: ${districtName} (${rentalType}) - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} ${rentalType === 'charter' ? '전세' : '월세'} 매물 목록`;
    }

    // 매물 목록 컨테이너 가져오기
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('컨테이너를 찾을 수 없습니다');
        return;
    }

    // 기존 내용 지우기
    container.innerHTML = '';

    // 매물이 있는지 확인
    if (!district.top_properties || district.top_properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 매물이 없습니다.</div>
            </div>
        `;
    } else {
        // 매물 카드들 생성
        district.top_properties.forEach((property, index) => {
            const card = createSimplePropertyCard(property, index + 1, rentalType);
            container.appendChild(card);
        });
    }

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 전세 지역구 카드 생성 - 상세 순위 정보 버튼 기능 추가
 */
function createCharterDistrictCard(district, rank) {
    const card = document.createElement('div');
    card.className = 'charter_district_card';
    card.id = `charter_district_card_${rank}`;

    const topProperty = district.top_properties && district.top_properties.length > 0
        ? district.top_properties[0] : null;

    const priceText = topProperty ? formatCharterPrice(topProperty) : '매물 정보 없음';
    const scoreText = topProperty ? `${topProperty.final_score.toFixed(1)}점` : '-';

    card.innerHTML = `
        <div class="charter_district_header">
            <span class="charter_district_rank">${rank}.</span>
            <span class="charter_district_name">${district.district_name}</span>
        </div>
        
        <div class="charter_district_info">
            <div class="charter_info_row">
                <span class="charter_info_label">대표 매물:</span>
                <span class="charter_info_value charter_price_value">${priceText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 점수:</span>
                <span class="charter_info_value charter_score_value">${scoreText}</span>
            </div>
            <div class="charter_info_row">
                <span class="charter_info_label">추천 근거:</span>
                <span class="charter_info_value">${district.summary || '정보 없음'}</span>
            </div>
        </div>
        
        <div class="charter_district_buttons">
            <button class="charter_btn_detail_rank" onclick="showDetailRankModal('${district.district_name}', 'charter', ${rank})">지역구 추천 정보</button>
            <button class="charter_btn_property_list" onclick="showCharterPropertyListModal('${district.district_name}', ${rank})">상세 매물들 보기</button>
        </div>
    `;

    return card;
}

/**
 * 매물 모달 닫기
 */
function closePropertyModal() {
    const modal = document.getElementById('property_detail_modal');
    if (modal) {
        modal.style.display = 'none';
    }
    document.body.classList.remove('modal_open');
}

/**
 * 매물 목록 렌더링 (수정된 버전)
 */
function renderPropertyList(properties, rentalType) {
    console.log('매물 목록 렌더링:', properties, rentalType);

    const container = document.getElementById('property_list_container');
    console.log('매물 리스트 컨테이너:', container);

    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div class="property_empty_message">
                <div class="property_empty_icon">🏠</div>
                <div class="property_empty_text">해당 지역구에 조건에 맞는 매물이 없습니다.</div>
            </div>
        `;
        console.log('매물이 없어서 빈 메시지 표시');
        return;
    }

    // 점수 순으로 정렬 (높은 점수부터) - 수정된 필드명 사용
    const sortedProperties = [...properties].sort((a, b) => b.finalScore - a.finalScore);
    console.log('정렬된 매물 목록:', sortedProperties);

    sortedProperties.forEach((property, index) => {
        const card = createPropertyCard(property, index + 1, rentalType);
        container.appendChild(card);
    });

    console.log('매물 카드 생성 완료');
}

/**
 * 개별 매물 카드 생성 (수정된 버전)
 */
function createPropertyCard(property, rank, rentalType) {
    const card = document.createElement('div');

    // CSS에 정의된 클래스명 사용
    if (rentalType === 'charter') {
        card.className = 'charter_property_card';
    } else {
        card.className = 'monthly_property_card';
    }

    // 가격 형식화 - 임대 유형별 분리
    const priceText = rentalType === 'charter'
        ? formatCharterPrice(property)
        : formatMonthlyPrice(property);

    // 건축연도 처리 - 수정된 필드명 사용
    const buildYearText = property.buildYear ? `${property.buildYear}년` : '정보없음';

    // 층수 처리
    const floorText = property.floor ? `${property.floor}층` : '정보없음';

    // 전세/월세에 따른 클래스명 선택
    const headerClass = rentalType === 'charter' ? 'charter_property_header' : 'monthly_property_header';
    const titleClass = rentalType === 'charter' ? 'charter_property_title' : 'monthly_property_title';
    const nameClass = rentalType === 'charter' ? 'charter_property_name' : 'monthly_property_name';
    const scoreClass = rentalType === 'charter' ? 'charter_property_score' : 'monthly_property_score';
    const addressClass = rentalType === 'charter' ? 'charter_property_address' : 'monthly_property_address';
    const bodyClass = rentalType === 'charter' ? 'charter_property_body' : 'monthly_property_body';
    const gridClass = rentalType === 'charter' ? 'charter_property_details_grid' : 'monthly_property_details_grid';
    const itemClass = rentalType === 'charter' ? 'charter_property_detail_item' : 'monthly_property_detail_item';
    const labelClass = rentalType === 'charter' ? 'charter_property_detail_label' : 'monthly_property_detail_label';
    const valueClass = rentalType === 'charter' ? 'charter_property_detail_value' : 'monthly_property_detail_value';
    const highlightClass = rentalType === 'charter' ? 'charter_property_price_highlight' : 'monthly_property_price_highlight';

    card.innerHTML = `
        <div class="${headerClass}">
            <div class="${titleClass}">
                <div class="${nameClass}">${property.propertyName || '매물명 없음'}</div>
                <div class="${scoreClass}">${property.finalScore.toFixed(1)}점</div>
            </div>
            <div class="${addressClass}">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="${bodyClass}">
            <div class="${gridClass}">
                <div class="${itemClass} ${highlightClass}">
                    <div class="${labelClass}">가격</div>
                    <div class="${valueClass}">${priceText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">평수</div>
                    <div class="${valueClass}">${property.area}평</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">건축연도</div>
                    <div class="${valueClass}">${buildYearText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">층수</div>
                    <div class="${valueClass}">${floorText}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">임대유형</div>
                    <div class="${valueClass}">${property.leaseType || '정보없음'}</div>
                </div>
                
                <div class="${itemClass}">
                    <div class="${labelClass}">순위</div>
                    <div class="${valueClass}">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

/**
 * 모달 이벤트 리스너 설정 - 상세 순위 모달 추가
 */
function setupModalEventListeners() {
    // 기존 매물 모달
    const propertyModal = document.getElementById('property_detail_modal');
    const propertyOverlay = document.querySelector('.property_modal_overlay');

    if (propertyOverlay) {
        propertyOverlay.addEventListener('click', function(e) {
            if (e.target === propertyOverlay) {
                closePropertyModal();
            }
        });
    }

    // === 2차 명세: 상세 순위 모달 이벤트 리스너 추가 ===
    const rankModal = document.getElementById('detail_rank_modal');
    const rankOverlay = document.querySelector('.detail_rank_modal_overlay');

    if (rankOverlay) {
        rankOverlay.addEventListener('click', function(e) {
            if (e.target === rankOverlay) {
                closeDetailRankModal();
            }
        });
    }

    // ESC 키로 모달 닫기 (기존 코드 수정)
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            if (propertyModal && propertyModal.style.display === 'block') {
                closePropertyModal();
            }
            if (rankModal && rankModal.style.display === 'block') {
                closeDetailRankModal();
            }
        }
    });
}

/**
 * 에러 표시
 */
function showError(errorElement, message) {
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.classList.add('show');
    }
}

/**
 * 에러 숨김
 */
function hideError(errorElement) {
    if (errorElement) {
        errorElement.textContent = '';
        errorElement.classList.remove('show');
    }
}

/**
 * 로딩 상태 표시
 */
function showLoading() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (submitButton) {
        submitButton.disabled = true;
        submitButton.value = '추천 결과 검색 중...';
        submitButton.style.backgroundColor = '#ccc';
        submitButton.style.cursor = 'not-allowed';
    }
}

/**
 * 로딩 상태 숨김
 */
function hideLoading() {
    let submitButton = document.querySelector('#recommend_result_btn input[type="button"]');

    if (!submitButton) {
        submitButton = document.querySelector('#recommend_result input[type="button"]');
    }

    if (submitButton) {
        submitButton.disabled = false;
        submitButton.value = '추천 결과 확인';
        updateSubmitButtonState();
    }
}

/**
 * 초기 검증 설정
 */
function initializeValidation() {
    const inputs = document.querySelectorAll('input, select');
    inputs.forEach(input => {
        input.addEventListener('input', updateSubmitButtonState);
        input.addEventListener('change', updateSubmitButtonState);
    });
}

/**
 * 상세 순위 정보 모달 표시
 */
function showDetailRankModal(districtName, rentalType, rank) {
    console.log(`상세 순위 정보 모달 표시: ${districtName} (${rentalType}) - 순위: ${rank}`);

    const modal = document.getElementById('detail_rank_modal');
    if (!modal) {
        console.error('상세 순위 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('detail_rank_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 상세 순위 정보`;
    }

    // 기본 정보 설정
    const rankDistrictName = document.getElementById('rank_district_name');
    const rankPosition = document.getElementById('rank_position');

    if (rankDistrictName) rankDistrictName.textContent = districtName;
    if (rankPosition) rankPosition.textContent = `${rank}위`;

    // === 2차 명세: 3개 점수 데이터 표시 ===
    updateScoreDisplay('average_price_score', 'price_score_bar', district.averagePriceScore || 0);
    updateScoreDisplay('average_space_score', 'space_score_bar', district.averageSpaceScore || 0);
    updateScoreDisplay('district_safety_score', 'safety_score_bar', district.districtSafetyScore || 0);

    // 종합 평가 텍스트 설정
    const summaryText = document.getElementById('rank_summary_text');
    if (summaryText) {
        summaryText.textContent = district.summary || '이 지역구에 대한 상세 평가 정보입니다.';
    }

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 점수 표시 업데이트 (점수값과 프로그레스 바)
 */
function updateScoreDisplay(scoreElementId, barElementId, score) {
    // 점수 텍스트 업데이트
    const scoreElement = document.getElementById(scoreElementId);
    if (scoreElement) {
        scoreElement.textContent = `${score.toFixed(1)}점`;
    }

    // 프로그레스 바 업데이트
    const barElement = document.getElementById(barElementId);
    if (barElement) {
        // 0-100 범위로 정규화
        const normalizedScore = Math.max(0, Math.min(100, score));
        barElement.style.width = normalizedScore + '%';
    }
}

/**
 * 상세 순위 모달 닫기
 */
function closeDetailRankModal() {
    const modal = document.getElementById('detail_rank_modal');
    if (modal) {
        modal.style.display = 'none';
    }
    document.body.classList.remove('modal_open');
}

// === 완전히 새로운 매물 리스트 표시 함수들 ===

/**
 * 전세 매물 리스트 모달 표시 (완전히 새로운 함수)
 */
function showCharterPropertyListModal(districtName, rank) {
    console.log(`전세 매물 리스트 모달 표시: ${districtName} - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('매물 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 전세 매물 목록`;
    }

    // 매물 목록 렌더링
    renderCharterPropertyList(district.top_properties || []);

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 월세 매물 리스트 모달 표시 (완전히 새로운 함수)
 */
function showMonthlyPropertyListModal(districtName, rank) {
    console.log(`월세 매물 리스트 모달 표시: ${districtName} - 순위: ${rank}`);

    const modal = document.getElementById('property_detail_modal');
    if (!modal) {
        console.error('매물 모달을 찾을 수 없습니다!');
        return;
    }

    if (!currentRecommendationData || !currentRecommendationData.recommendedDistricts) {
        console.error('추천 데이터가 없습니다');
        return;
    }

    // 해당 지역구 찾기
    const district = currentRecommendationData.recommendedDistricts.find(d => d.district_name === districtName);
    if (!district) {
        console.error(`지역구를 찾을 수 없습니다: ${districtName}`);
        return;
    }

    // 모달 제목 설정
    const modalTitle = document.getElementById('property_modal_title');
    if (modalTitle) {
        modalTitle.textContent = `${districtName} 월세 매물 목록`;
    }

    // 매물 목록 렌더링
    renderMonthlyPropertyList(district.top_properties || []);

    // 모달 표시
    modal.style.display = 'block';
    document.body.classList.add('modal_open');
}

/**
 * 전세 매물 목록 렌더링 (완전히 새로운 함수)
 */
function renderCharterPropertyList(properties) {
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 전세 매물이 없습니다.</div>
            </div>
        `;
        return;
    }

    // 매물 카드들 생성
    properties.forEach((property, index) => {
        const card = createCharterPropertyCard(property, index + 1);
        container.appendChild(card);
    });
}

/**
 * 월세 매물 목록 렌더링 (완전히 새로운 함수)
 */
function renderMonthlyPropertyList(properties) {
    const container = document.getElementById('property_list_container');
    if (!container) {
        console.error('property_list_container를 찾을 수 없습니다');
        return;
    }

    container.innerHTML = '';

    if (!properties || properties.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 50px 20px; color: #666;">
                <div style="font-size: 48px; margin-bottom: 20px;">🏠</div>
                <div style="font-size: 16px;">해당 지역구에 조건에 맞는 월세 매물이 없습니다.</div>
            </div>
        `;
        return;
    }

    // 매물 카드들 생성
    properties.forEach((property, index) => {
        const card = createMonthlyPropertyCard(property, index + 1);
        container.appendChild(card);
    });
}

/**
 * 전세 개별 매물 카드 생성 (완전히 새로운 함수)
 */
function createCharterPropertyCard(property, rank) {
    const card = document.createElement('div');
    card.className = 'charter_property_card';

    const priceText = `전세 ${(property.price || 0).toLocaleString()}만원`;
    const buildYearText = property.build_year ? `${property.build_year}년` : '정보없음';
    const floorText = property.floor ? `${property.floor}층` : '정보없음';
    const scoreText = property.final_score ? `${property.final_score.toFixed(1)}점` : '-';

    card.innerHTML = `
        <div class="charter_property_header">
            <div class="charter_property_title">
                <div class="charter_property_name">${property.property_name || '매물명 없음'}</div>
                <div class="charter_property_score">${scoreText}</div>
            </div>
            <div class="charter_property_address">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="charter_property_body">
            <div class="charter_property_details_grid">
                <div class="charter_property_detail_item charter_property_price_highlight">
                    <div class="charter_property_detail_label">가격</div>
                    <div class="charter_property_detail_value">${priceText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">평수</div>
                    <div class="charter_property_detail_value">${parseFloat(property.area || 0).toFixed(1)}평</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">건축연도</div>
                    <div class="charter_property_detail_value">${buildYearText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">층수</div>
                    <div class="charter_property_detail_value">${floorText}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">임대유형</div>
                    <div class="charter_property_detail_value">${property.lease_type || '전세'}</div>
                </div>
                
                <div class="charter_property_detail_item">
                    <div class="charter_property_detail_label">순위</div>
                    <div class="charter_property_detail_value">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

/**
 * 월세 개별 매물 카드 생성 (완전히 새로운 함수)
 */
function createMonthlyPropertyCard(property, rank) {
    const card = document.createElement('div');
    card.className = 'monthly_property_card';

    const deposit = (property.price || 0).toLocaleString();
    const monthly = (property.monthly_rent || 0).toLocaleString();
    const priceText = `보증금 ${deposit}만원 / 월세 ${monthly}만원`;
    const buildYearText = property.build_year ? `${property.build_year}년` : '정보없음';
    const floorText = property.floor ? `${property.floor}층` : '정보없음';
    const scoreText = property.final_score ? `${property.final_score.toFixed(1)}점` : '-';

    card.innerHTML = `
        <div class="monthly_property_header">
            <div class="monthly_property_title">
                <div class="monthly_property_name">${property.property_name || '매물명 없음'}</div>
                <div class="monthly_property_score">${scoreText}</div>
            </div>
            <div class="monthly_property_address">${property.address || '주소 정보 없음'}</div>
        </div>
        
        <div class="monthly_property_body">
            <div class="monthly_property_details_grid">
                <div class="monthly_property_detail_item monthly_property_price_highlight">
                    <div class="monthly_property_detail_label">가격</div>
                    <div class="monthly_property_detail_value">${priceText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">평수</div>
                    <div class="monthly_property_detail_value">${parseFloat(property.area || 0).toFixed(1)}평</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">건축연도</div>
                    <div class="monthly_property_detail_value">${buildYearText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">층수</div>
                    <div class="monthly_property_detail_value">${floorText}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">임대유형</div>
                    <div class="monthly_property_detail_value">${property.lease_type || '월세'}</div>
                </div>
                
                <div class="monthly_property_detail_item">
                    <div class="monthly_property_detail_label">순위</div>
                    <div class="monthly_property_detail_value">#${rank}</div>
                </div>
            </div>
        </div>
    `;

    return card;
}

// 전역 함수로 내보내기
window.showCharterPropertyListModal = showCharterPropertyListModal;
window.showMonthlyPropertyListModal = showMonthlyPropertyListModal;

// 전역 함수로 내보내기
window.showPropertyModal = showPropertyModal;
window.closePropertyModal = closePropertyModal;
// 전역 함수로 내보내기 - 상세 순위 모달 함수 추가
window.showDetailRankModal = showDetailRankModal;
window.closeDetailRankModal = closeDetailRankModal;