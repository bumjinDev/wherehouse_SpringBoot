package com.wherehouse.board.model;

import java.sql.Date;

import com.wherehouse.members.model.customVaild.RegionValid;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardDTO {
	
	private int boardId;
	private String userId;
	@NotBlank(message = "게시글 제목은 필수 입력값입니다.\n")
	private String title;
	@NotBlank(message = "게시글 내용은 필수 입력값입니다.")
	private String boardContent;
	@NotBlank(message = "지역글 내용은 필수 입력값입니다.")					// 의도적인 공백 방지
	@RegionValid(message = "지역은 서울시 25개 자치구 중 하나여야 합니다.") 	// 커스텀 지역 검증
	private String region;
	private int boardHit;
	private Date boardDate;
}
