package com.wherehouse.members.model.customVaild;

import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RegionValidator implements ConstraintValidator<RegionValid, String> {

    private static final Set<String> VALID_REGIONS = Set.of(
        "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구",
        "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구",
        "용산구", "은평구", "종로구", "중구", "중랑구", "미 선택"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && VALID_REGIONS.contains(value);
    }
}
