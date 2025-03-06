package com.wherehouse.board.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity

@Data					// @ToString, @EqualsAndHashCode, @Getter, @Setter, @RequiredArgsConstructor를 자동 완성
@AllArgsConstructor		// 생성자 생성을 대신 하는 어노테이션, 모든 멤버 변수를 매개변수로 담는 생성자를 내부적으로 자동 등록한다.
@NoArgsConstructor		// 생성자 생성을 대신 하는 어노테이션, 매개변수가 아에 없는 생성자를 생성한다, JPA 할 때 반드시 포함해야 된다.

@Table(name="commenttbl")
public class CommentEntity {
	
	@Column
	private int boardId;	// 테이블 'whereboard'
	
	@Column
	private String userId;	// 테이블 'memberstbl'
	
	@Column
	private String userName;
	
	@Column
	private String replyContent;
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "commenttbl_seq")
    @SequenceGenerator(name = "commenttbl_seq", sequenceName = "commenttbl_seq", allocationSize = 1)
	@Column(name="COMMENTPRIMARY")
	int commentPrimary;	// 'commentTbl' 기본 키 컬럼. (JPA.save())
	
	/*
	   CREATE SEQUENCE commenttbl_seq
	   START WITH 1
	   INCREMENT BY 1;
	*/
}
