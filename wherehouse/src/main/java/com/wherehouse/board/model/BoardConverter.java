package com.wherehouse.board.model;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class BoardConverter {

	 public BoardEntity toEntity(BoardDTO boardDTO) {
	        return new BoardEntity(boardDTO.getBoardId(), boardDTO.getUserId(), boardDTO.getTitle(), boardDTO.getBoardContent(), boardDTO.getRegion(), boardDTO.getBoardHit(), boardDTO.getBoardDate());
	    }

    public BoardDTO toDTO(BoardEntity boardEntity) {
        return new BoardDTO(boardEntity.getConnum(), boardEntity.getUserid(), boardEntity.getTitle(), boardEntity.getBoardcontent(), boardEntity.getRegion(), boardEntity.getHit(), boardEntity.getBdate());
    }
    
    public List<BoardDTO> toVOList(List<BoardEntity> boardEntities) {
        return boardEntities.stream()
            .map(this::toDTO) // BoardConverter의 toVO() 호출
            .collect(Collectors.toList());
    }
}
