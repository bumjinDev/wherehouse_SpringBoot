package com.wherehouse.board.model;

import java.sql.Date;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="whereboard")
public class BoardEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "whereboard_seq")
    @SequenceGenerator(name = "whereboard_seq", sequenceName = "whereboarder_seq", allocationSize = 1)
	private int connum;
	private String userid;
	private String title;
	private String boardcontent;
	private String region;
	private int hit;
	private Date bdate;

}
