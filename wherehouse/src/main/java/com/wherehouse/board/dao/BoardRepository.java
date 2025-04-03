package com.wherehouse.board.dao;

import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * BoardRepository
 *
 * - 게시판 관련 데이터베이스 작업을 처리하는 Repository 구현 클래스입니다.
 * - 게시글 및 댓글의 조회, 작성, 수정, 삭제, 조회수 증가 등의 기능을 제공합니다.
 * - 내부적으로 Spring Data JPA Repository를 활용하며, DB 예외 처리는 상위 계층에서 통합 관리합니다.
 */
@Service
public class BoardRepository implements IBoardRepository {

    private static final Logger logger = LoggerFactory.getLogger(BoardRepository.class);

    private final BoardEntityRepository boardEntityRepository;
    private final CommentEntityRepository commentEntityManger;

    public BoardRepository(BoardEntityRepository boardEntityRepository,
                           CommentEntityRepository commentEntityManger) {
        this.boardEntityRepository = boardEntityRepository;
        this.commentEntityManger = commentEntityManger;
    }

    /**
     * 게시글 목록 조회 (페이지 단위)
     *
     * - 페이지 번호에 따라 10건씩 페이징하여 최신 게시글을 반환합니다.
     * - 예외 상황: 게시글이 없어도 빈 리스트 반환 (예외 처리 불필요)
     *
     * @param pnIndex 페이지 번호 (0부터 시작)
     * @return 게시글 리스트
     */
    @Override
    public List<BoardEntity> searchBoardList(int pnIndex) {
        return boardEntityRepository.findByBdateWithPagination(pnIndex * 10, 10);
    }

    /**
     * 게시글 단건 조회
     *
     * - 게시글 ID에 해당하는 게시글을 조회합니다.
     *
     * @param boardId 게시글 ID
     * @return 게시글 Optional
     */
    @Override
    public Optional<BoardEntity> findBoard(int boardId) {
        logger.info("boardRepository.findBoard() - boardId: {}", boardId);
        return boardEntityRepository.findById(boardId);
    }

    /**
     * 게시글 작성
     *
     * - 신규 게시글을 저장합니다.
     * - ID가 없는 상태의 엔티티에 대해 save() 호출 시 INSERT 수행됩니다.
     *
     * @param boardEntity 저장할 게시글 엔티티
     */
    @Override
    public void boardWrite(BoardEntity boardEntity) {
        boardEntityRepository.save(boardEntity);
    }

    /**
     * 게시글 수정
     *
     * - ID가 포함된 게시글 엔티티를 저장합니다.
     * - JPA의 save()는 동일한 ID 존재 시 UPDATE로 동작합니다.
     * - 컬럼 분할 업데이트는 추후 별도 메서드로 분리 예정
     *
     * @param boardEntity 수정할 게시글 엔티티
     */
    public void boardModify(BoardEntity boardEntity) {
        boardEntityRepository.save(boardEntity);
    }

    /**
     * 게시글 삭제
     *
     * - 지정한 ID의 게시글을 삭제합니다.
     * - 대상이 존재하지 않을 경우 EmptyResultDataAccessException 발생 (컨트롤러에서 처리)
     *
     * @param boardId 삭제할 게시글 ID
     */
    public void deleteBoard(int boardId) {
        boardEntityRepository.deleteById(boardId);
    }

    /**
     * 게시글 조회수 증가
     *
     * - 지정된 게시글 ID의 조회수를 1 증가시킵니다.
     * - 내부적으로 @Modifying update 쿼리 실행
     *
     * @param boardId 조회수 증가 대상 게시글 ID
     * @return 정상 처리 시 1, 실패 시 0
     */
    public int upHit(int boardId) {
        return boardEntityRepository.updateHitByConnum(boardId, 1);
    }

    /**
     * 댓글 목록 조회
     *
     * - 특정 게시글에 대한 댓글 리스트를 반환합니다.
     * - 게시글이 없어도 빈 리스트 반환 (예외 처리 불필요)
     *
     * @param commentId 게시글 ID
     * @return 댓글 리스트
     */
    @Override
    public List<CommentEntity> commentSearch(int commentId) {
        return commentEntityManger.findByBoardId(commentId);
    }

    /**
     * 댓글 작성
     *
     * - 지정된 게시글에 댓글을 추가합니다.
     *
     * @param comment 저장할 댓글 엔티티
     */
    @Override
    public void replyWrite(CommentEntity comment) {
        commentEntityManger.save(comment);
    }
}
