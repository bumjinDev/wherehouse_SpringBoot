package com.wherehouse.recommand.model;

import jakarta.validation.constraints.*;
import lombok.Data;

/* 전세금 요청에 대한 요청 받는 VO. 유효성 검증 위함 */
@Data	// 각 멤버 변수 별 getter, setter 자동 생성 : @Vaild 검사 위해 필수
public class RecMonthlyServiceRequestVO {
	
	@NotNull(message = "월세 보증금 평균 금액 입력은 필수 입니다.")
	@Min(value = 2100, message = "월세 보증금 평균 금액은 2100 이상이어야 합니다.")
	@Max(value = 4500, message = "월세 보증금 평균 금액은 4500 이하이어야 합니다.")
	int deposit_avg;
	
	@NotNull(message = "월세금 평균 금액 입력은 필수 입니다.")
	@Min(value = 40, message = "월세금 평균 금액은 40 이상이어야 합니다.")
	@Max(value = 70, message = "월세금 평균 금액은 70 이하이어야 합니다.")
	int monthly_avg;
	
	@NotNull(message = "안정성 점수는 필수 입니다.")
	@Min(value = 1, message = "안정성 점수는 1 이상 이어야 합니다.")
	@Max(value = 10, message = "안정성 점수는 10 이하 이어야 합니다.")
	int safe_score;
	
	@NotNull(message = "편의성 점수는 필수 입니다.")
	@Min(value = 1, message = "편의성 점수는 1 이상 이어야 합니다.")
	@Max(value = 10, message = "편의성 점수는 10 이하 이어야 합니다.")
	int cvt_score;
}
