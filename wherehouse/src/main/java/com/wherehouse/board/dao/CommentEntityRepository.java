package com.wherehouse.board.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.wherehouse.board.model.CommentEntity;

/**
 * CommentEntityManager:
 * 댓글 관리를 위한 데이터 접근 계층.
 * JPA를 통해 댓글 테이블(CommentEntity)에 접근하여 CRUD 작업을 수행합니다.
 */
public interface CommentEntityRepository extends JpaRepository<CommentEntity, Integer> {

    /**
     * 특정 게시글 번호(commentId)에 해당하는 댓글 목록을 조회합니다.
     *
     * @param commentId 댓글이 속한 게시글 번호
     * @return 게시글에 속한 댓글 목록
     */
    List<CommentEntity> findByNum(int commentId);

    // 기본적으로 제공되는 save 메서드는 그대로 사용
}
