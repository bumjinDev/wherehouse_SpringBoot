<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>리뷰 게시판</title>
    <script src="https://code.jquery.com/jquery-3.4.1.js"></script>
    <script src="https://kit.fontawesome.com/09b067fdc5.js" crossorigin="anonymous"></script>
    
    <link rel="stylesheet" href="../css/review_board.css">
</head>

<body>
<!-- 메인 컨테이너 -->
<div id="review_board_container">
    
    <!-- 사이드바 -->
    <div id="information">
        <div id="btn">▼</div>
        <aside id="side-bar">
            
            <!-- 헤더 -->
            <div class="review_board_header">
                리뷰 게시판
            </div>
            
            <!-- 필터 및 정렬 섹션 -->
            <div id="filter_section">
                <div class="filter_box">
                    <p>필터 및 정렬</p>
                    <hr class="section_hr">
                    
                    <!-- 정렬 옵션 -->
                    <div class="filter_item">
                        <div class="filter_label">정렬 기준:</div>
                        <select class="filter_select" id="sort_select">
                            <option value="latest">최신순</option>
                            <option value="rating_desc">별점 높은순</option>
                            <option value="rating_asc">별점 낮은순</option>
                        </select>
                    </div>
                    
                    <!-- 별점 필터 -->
                    <div class="filter_item">
                        <div class="filter_label">별점 필터:</div>
                        <select class="filter_select" id="rating_filter">
                            <option value="">전체</option>
                            <option value="5">⭐⭐⭐⭐⭐</option>
                            <option value="4">⭐⭐⭐⭐</option>
                            <option value="3">⭐⭐⭐</option>
                            <option value="2">⭐⭐</option>
                            <option value="1">⭐</option>
                        </select>
                    </div>
                    
                    <!-- 매물 ID 검색 -->
                    <div class="filter_item">
                        <div class="filter_label">매물 ID 검색:</div>
                        <div class="search_input_group">
                            <input type="text" id="property_id_search" placeholder="32자 MD5 Hash" maxlength="32">
                            <button class="search_clear_btn" id="clear_property_id">✕</button>
                        </div>
                        <div class="filter_desc">특정 매물의 리뷰만 조회</div>
                    </div>
                    
                    <!-- 필터 적용 버튼 -->
                    <div class="filter_apply_btn" id="apply_filter_btn">
                        <button>필터 적용</button>
                    </div>
                </div>
                
                <!-- 리뷰 작성 버튼 -->
                <div class="write_review_btn_box" id="write_review_btn">
                    <button>
                        <i class="fas fa-pen"></i> 리뷰 작성하기
                    </button>
                </div>
            </div>
            
            <!-- 통계 정보 (특정 매물 필터 시 표시) -->
            <div id="property_stats_section" style="display: none;">
                <div class="stats_box">
                    <p>매물 통계</p>
                    <hr class="section_hr">
                    
                    <div class="stat_item">
                        <span class="stat_label">매물 ID:</span>
                        <span class="stat_value" id="filtered_property_id">-</span>
                    </div>
                    
                    <div class="stat_item">
                        <span class="stat_label">평균 평점:</span>
                        <span class="stat_value stat_highlight" id="property_avg_rating">0.0</span>
                    </div>
                </div>
            </div>
            
        </aside>
    </div>
    
    <!-- 메인 콘텐츠 영역 -->
    <div id="main_content">
        
        <!-- 헤더 정보 -->
        <div id="content_header">
            <h2>전체 리뷰</h2>
            <div class="total_count">
                총 <span id="total_reviews">0</span>개의 리뷰
            </div>
        </div>
        
        <!-- 리뷰 카드 리스트 -->
        <div id="review_list_container">
            <!-- JavaScript로 동적 생성 -->
        </div>
        
        <!-- 페이지네이션 -->
        <div id="pagination_container">
            <!-- JavaScript로 동적 생성 -->
        </div>
        
    </div>
    
</div>

<!-- 리뷰 작성 모달 -->
<div id="write_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content">
            <div class="modal_header">
                <h3 id="write_modal_title">리뷰 작성</h3>
                <button class="modal_close" onclick="closeWriteModal()">&times;</button>
            </div>
            <div class="modal_body">
                <form id="review_form">
                    
                    <!-- 매물 ID -->
                    <div class="form_group">
                        <label for="input_property_id">매물 ID <span class="required">*</span></label>
                        <input type="text" id="input_property_id" placeholder="32자 MD5 Hash" maxlength="32" required>
                        <div class="form_desc">매물의 고유 식별자 (32자)</div>
                    </div>
                    
                    <!-- 사용자 ID -->
                    <div class="form_group">
                        <label for="input_user_id">사용자 ID <span class="required">*</span></label>
                        <input type="text" id="input_user_id" placeholder="user_1234" maxlength="50" required>
                        <div class="form_desc">작성자 식별자 (최대 50자)</div>
                    </div>
                    
                    <!-- 별점 -->
                    <div class="form_group">
                        <label for="input_rating">별점 <span class="required">*</span></label>
                        <div class="rating_input">
                            <select id="input_rating" required>
                                <option value="">선택</option>
                                <option value="5">⭐⭐⭐⭐⭐ (5점)</option>
                                <option value="4">⭐⭐⭐⭐ (4점)</option>
                                <option value="3">⭐⭐⭐ (3점)</option>
                                <option value="2">⭐⭐ (2점)</option>
                                <option value="1">⭐ (1점)</option>
                            </select>
                        </div>
                    </div>
                    
                    <!-- 리뷰 내용 -->
                    <div class="form_group">
                        <label for="input_content">리뷰 내용 <span class="required">*</span></label>
                        <textarea id="input_content" rows="8" placeholder="최소 20자 이상, 최대 1000자 이하로 작성해주세요" maxlength="1000" required></textarea>
                        <div class="char_count">
                            <span id="current_char_count">0</span> / 1000자
                        </div>
                    </div>
                    
                    <!-- 에러 메시지 -->
                    <div class="error_message" id="form_error_message"></div>
                    
                    <!-- 버튼 -->
                    <div class="form_buttons">
                        <button type="button" class="btn_cancel" onclick="closeWriteModal()">취소</button>
                        <button type="submit" class="btn_submit">작성 완료</button>
                    </div>
                    
                </form>
            </div>
        </div>
    </div>
</div>

<!-- 리뷰 상세보기 모달 -->
<div id="detail_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content modal_content_large">
            <div class="modal_header">
                <h3>리뷰 상세</h3>
                <button class="modal_close" onclick="closeDetailModal()">&times;</button>
            </div>
            <div class="modal_body">
                
                <!-- 리뷰 상세 정보 -->
                <div class="detail_section">
                    <div class="detail_header">
                        <div class="detail_rating" id="detail_rating"></div>
                        <div class="detail_date" id="detail_created_at"></div>
                    </div>
                    
                    <div class="detail_info">
                        <div class="detail_info_row">
                            <span class="detail_label">작성자:</span>
                            <span class="detail_value" id="detail_user_id"></span>
                        </div>
                        <div class="detail_info_row">
                            <span class="detail_label">매물 ID:</span>
                            <span class="detail_value" id="detail_property_id"></span>
                        </div>
                    </div>
                    
                    <div class="detail_tags" id="detail_tags">
                        <!-- JavaScript로 동적 생성 -->
                    </div>
                    
                    <div class="detail_content" id="detail_content">
                        <!-- JavaScript로 동적 생성 -->
                    </div>
                    
                    <div class="detail_updated" id="detail_updated_at" style="display: none;">
                        <i class="fas fa-edit"></i> 수정됨: <span id="detail_updated_time"></span>
                    </div>
                </div>
                
                <!-- 버튼 영역 -->
                <div class="detail_buttons">
                    <button class="btn_edit" id="btn_edit_review">
                        <i class="fas fa-edit"></i> 수정
                    </button>
                    <button class="btn_delete" id="btn_delete_review">
                        <i class="fas fa-trash"></i> 삭제
                    </button>
                </div>
                
            </div>
        </div>
    </div>
</div>

<!-- 리뷰 수정 모달 -->
<div id="edit_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content">
            <div class="modal_header">
                <h3>리뷰 수정</h3>
                <button class="modal_close" onclick="closeEditModal()">&times;</button>
            </div>
            <div class="modal_body">
                <form id="review_edit_form">
                    
                    <input type="hidden" id="edit_review_id">
                    
                    <!-- 별점 -->
                    <div class="form_group">
                        <label for="edit_rating">별점 <span class="required">*</span></label>
                        <div class="rating_input">
                            <select id="edit_rating" required>
                                <option value="5">⭐⭐⭐⭐⭐ (5점)</option>
                                <option value="4">⭐⭐⭐⭐ (4점)</option>
                                <option value="3">⭐⭐⭐ (3점)</option>
                                <option value="2">⭐⭐ (2점)</option>
                                <option value="1">⭐ (1점)</option>
                            </select>
                        </div>
                    </div>
                    
                    <!-- 리뷰 내용 -->
                    <div class="form_group">
                        <label for="edit_content">리뷰 내용 <span class="required">*</span></label>
                        <textarea id="edit_content" rows="8" placeholder="최소 20자 이상, 최대 1000자 이하로 작성해주세요" maxlength="1000" required></textarea>
                        <div class="char_count">
                            <span id="edit_char_count">0</span> / 1000자
                        </div>
                    </div>
                    
                    <!-- 에러 메시지 -->
                    <div class="error_message" id="edit_error_message"></div>
                    
                    <!-- 버튼 -->
                    <div class="form_buttons">
                        <button type="button" class="btn_cancel" onclick="closeEditModal()">취소</button>
                        <button type="submit" class="btn_submit">수정 완료</button>
                    </div>
                    
                </form>
            </div>
        </div>
    </div>
</div>

<!-- 삭제 확인 모달 -->
<div id="delete_confirm_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content modal_content_small">
            <div class="modal_header">
                <h3>리뷰 삭제</h3>
                <button class="modal_close" onclick="closeDeleteConfirmModal()">&times;</button>
            </div>
            <div class="modal_body">
                <div class="confirm_message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>정말로 이 리뷰를 삭제하시겠습니까?</p>
                    <p class="confirm_desc">삭제된 리뷰는 복구할 수 없습니다.</p>
                </div>
                
                <div class="form_buttons">
                    <button type="button" class="btn_cancel" onclick="closeDeleteConfirmModal()">취소</button>
                    <button type="button" class="btn_delete" id="confirm_delete_btn">삭제</button>
                </div>
            </div>
        </div>
    </div>
</div>

<script src="/wherehouse/js/review_board.js"></script>
</body>
</html>
