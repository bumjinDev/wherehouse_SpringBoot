package com.wherehouse.board.model;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class BoardConverter {

	 public BoardEntity toEntity(BoardVO boardVO) {
	        return new BoardEntity(boardVO.getBoardId(), boardVO.getUserId(), boardVO.getTitle(), boardVO.getBoardContent(), boardVO.getRegion(), boardVO.getBoardHit(), boardVO.getBoardDate());
	    }

    public BoardVO toVO(BoardEntity boardEntity) {
        return new BoardVO(boardEntity.getConnum(), boardEntity.getUserid(), boardEntity.getTitle(), boardEntity.getBoardcontent(), boardEntity.getRegion(), boardEntity.getHit(), boardEntity.getBdate());
    }
    
    public List<BoardVO> toVOList(List<BoardEntity> boardEntities) {
        return boardEntities.stream()
            .map(this::toVO) // BoardConverter의 toVO() 호출
            .collect(Collectors.toList());
    }
}
