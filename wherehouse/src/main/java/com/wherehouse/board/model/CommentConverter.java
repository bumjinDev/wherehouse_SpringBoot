package com.wherehouse.board.model;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class CommentConverter {

    public CommentEntity toEntity(CommandtVO vo) {
        if (vo == null) return null;
        return new CommentEntity(vo.getBoardId(), vo.getUserId(), vo.getUserName(), vo.getReplyContent(), 0);
    }

    public CommandtVO toVO(CommentEntity entity) {
        if (entity == null) return null;
        return new CommandtVO(entity.getBoardId() , entity.getUserId(), entity.getUserName(), entity.getReplyContent());
    }

    public List<CommentEntity> toEntityList(List<CommandtVO> voList) {
        if (voList == null || voList.isEmpty()) return List.of();
        return voList.stream().map(this::toEntity).collect(Collectors.toList());
    }

    public List<CommandtVO> toVOList(List<CommentEntity> entityList) {
        if (entityList == null || entityList.isEmpty()) return List.of();
        return entityList.stream().map(this::toVO).collect(Collectors.toList());
    }
}
