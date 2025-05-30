package com.wherehouse.board.model;

import java.sql.Date;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name="whereboard")
public class BoardEntity {
	
	
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "whereboard_seq")
    @SequenceGenerator(name = "whereboard_seq", sequenceName = "whereboarder_seq", allocationSize = 1)
	@Column(name = "CONNUM")  // 대소문자 정확히 일치
	@Id
	private int connum;
	private String userid;
	private String title;
	private String boardcontent;
	private String region;
	private int hit;
	private Date bdate;
}
