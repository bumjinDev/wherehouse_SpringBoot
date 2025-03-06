package com.wherehouse.board.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommandtVO {

	private int boardId;	// 테이블 'whereboard'

	private String userId;	// 테이블 'memberstbl'

	private String userName;

	private String replyContent;
}
