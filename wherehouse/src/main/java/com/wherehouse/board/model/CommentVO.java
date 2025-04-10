package com.wherehouse.board.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentVO {	/* 테이블 'whereboard' */
	
	@NotNull(message = "게시글 ID는 필수 입력값입니다.")
	private int boardId;
	private String userId;
	private String userName;
	@NotBlank(message = "댓글 내용은 필수 입력 사항 입니다.")
	private String replyContent;
}
