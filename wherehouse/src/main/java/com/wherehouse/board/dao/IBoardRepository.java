package com.wherehouse.board.dao;

import java.util.List;
import java.util.Optional;

import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommentEntity;

/**
 * IBoardRepository
 *
 * 게시글 및 댓글 관련 DB 접근을 위한 Repository 인터페이스.
 * - 게시글 생성, 조회, 수정, 삭제
 * - 조회수 증가, 댓글 생성, 댓글 조회
 *
 * 실제 쿼리나 예외 처리는 구현체에서 처리합니다.
 */
public interface IBoardRepository {

    /**
     * 게시글 목록을 페이지 단위로 조회합니다.
     *
     * @param pnIndex 0부터 시작하는 페이지 인덱스
     * @return 해당 페이지에 해당하는 게시글 목록 (최대 10건 등 페이지 크기는 구현체에서 결정)
     */
    List<BoardEntity> findAllByPage(int pnIndex);

    /**
     * 새로운 게시글을 생성합니다.
     *
     * @param boardEntity 작성할 게시글 엔티티
     * @return DB에 저장된 게시글 엔티티 (생성된 PK 등 포함)
     */
    BoardEntity createBoard(BoardEntity boardEntity);

    /**
     * 특정 게시글을 ID로 조회합니다.
     *
     * @param boardId 게시글 식별자(PK)
     * @return Optional<BoardEntity>, 게시글이 없으면 Optional.empty()
     */
    Optional<BoardEntity> findById(int boardId);

    /**
     * 특정 게시글의 조회수를 1 증가시킵니다.
     *
     * @param boardId 조회수 증가 대상 게시글 ID
     * @return 증가가 성공하면 1, 실패 시 0 등의 값을 반환 (구현체에서 결정)
     */
    int incrementHitCount(int boardId);

    /**
     * 특정 게시글을 삭제합니다.
     *
     * @param boardId 삭제 대상 게시글 ID
     */
    void deleteBoard(int boardId);

    /**
     * 게시글을 수정(업데이트)합니다.
     *
     * @param boardEntity 수정할 게시글 엔티티
     */
    void updateBoard(BoardEntity boardEntity);

    /**
     * 특정 게시글에 댓글을 작성합니다.
     *
     * @param comment 생성할 댓글 엔티티
     */
    void createComment(CommentEntity comment);

    /**
     * 특정 게시글에 속한 댓글 목록을 조회합니다.
     *
     * @param commentId 게시글 ID (댓글이 속해 있는 게시글 식별자)
     * @return 해당 게시글에 달린 댓글 목록
     */
    List<CommentEntity> findCommentsByBoardId(int commentId);
}
