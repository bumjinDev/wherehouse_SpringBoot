package com.wherehouse.board.model;

import java.sql.Date;

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
	private String title;
	private String boardContent;
	private String region;
	private int boardHit;
	private Date boardDate;
}
