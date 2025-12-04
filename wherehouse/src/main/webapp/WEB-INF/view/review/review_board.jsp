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
<div id="review_board_container">

    <div id="information">
        <div id="btn">▼</div>
        <aside id="side-bar">

            <div class="review_board_header">
                리뷰 게시판
            </div>

            <div id="filter_section">
                <div class="filter_box">
                    <p>필터 및 정렬</p>
                    <hr class="section_hr">

                    <div class="filter_item">
                        <div class="filter_label">정렬 기준:</div>
                        <select class="filter_select" id="sort_select">
                            <option value="rating_desc">최신순</option>
                            <option value="rating_asc">오래된순</option>
                        </select>
                    </div>

                    <div class="filter_item">
                        <div class="filter_label">매물 이름 (선택):</div>
                        <div style="position: relative;">
                            <input type="text" class="filter_select" id="filter_property_name"
                                   placeholder="예: 삼성아파트" maxlength="50" autocomplete="off">

                            <ul id="filter_results" class="autocomplete_list"></ul>
                        </div>
                        <div class="filter_desc">매물 이름으로 리뷰를 검색합니다.</div>
                    </div>

                    <div class="filter_item">
                        <div class="filter_label">페이지 번호:</div>
                        <input type="number" class="filter_select" id="page_input" min="1" value="1">
                    </div>

                    <input type="hidden" id="size_select" value="10">
                    <input type="hidden" id="search_type_select" value="all">

                    <div class="filter_item">
                        <div class="filter_label">키워드 검색:</div>
                        <div class="search_input_group">
                            <input type="text" id="keyword_search" placeholder="내용 또는 태그 검색" maxlength="100">
                            <button class="search_clear_btn" id="clear_keyword">✕</button>
                        </div>
                        <div class="filter_desc">최대 100자</div>
                    </div>

                    <div class="filter_apply_btn">
                        <button id="apply_filter_btn">필터 적용</button>
                    </div>
                </div>

                <div class="write_review_btn_box">
                    <button id="write_review_btn">
                        <i class="fas fa-pen"></i> 리뷰 작성하기
                    </button>
                </div>
            </div>

        </aside>
    </div>

    <div id="main_content">

        <div id="content_header">
            <h2>전체 리뷰 목록</h2>
            <div class="total_count">
                현재 페이지 리뷰
            </div>
        </div>

        <div id="review_list_container">
        </div>

        <div id="pagination_container">
        </div>

    </div>

</div>

<div id="write_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content">
            <div class="modal_header">
                <h3 id="write_modal_title">리뷰 작성</h3>
                <button class="modal_close" onclick="close_write_modal()">&times;</button>
            </div>
            <div class="modal_body">
                <form id="review_form">

                    <div class="form_group">
                        <label for="input_property_name">매물 이름 <span class="required">*</span></label>
                        <div style="position: relative;">
                            <input type="text" id="input_property_name"
                                   placeholder="예: 삼성아파트" maxlength="50" required autocomplete="off">

                            <input type="hidden" id="selected_property_id">

                            <ul id="modal_results" class="autocomplete_list"></ul>
                        </div>
                        <div class="form_desc">리뷰를 작성할 매물의 정확한 이름을 입력해주세요.</div>
                    </div>

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

                    <div class="form_group">
                        <label for="input_content">리뷰 내용 <span class="required">*</span></label>
                        <textarea id="input_content" rows="8" placeholder="최소 20자 이상, 최대 1000자 이하로 작성해주세요" maxlength="1000" required></textarea>
                        <div class="char_count">
                            <span id="current_char_count">0</span> / 1000자
                        </div>
                    </div>

                    <div class="error_message" id="form_error_message"></div>

                    <div class="form_buttons">
                        <button type="button" class="btn_cancel" onclick="close_write_modal()">취소</button>
                        <button type="submit" class="btn_submit">작성 완료</button>
                    </div>

                </form>
            </div>
        </div>
    </div>
</div>

<div id="detail_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content modal_content_large">
            <div class="modal_header">
                <h3>리뷰 상세</h3>
                <button class="modal_close" onclick="close_detail_modal()">&times;</button>
            </div>
            <div class="modal_body">
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
                    <div class="detail_tags" id="detail_tags"></div>
                    <div class="detail_content" id="detail_content"></div>
                    <div class="detail_updated" id="detail_updated_at" style="display: none;">
                        <i class="fas fa-edit"></i> 수정됨: <span id="detail_updated_time"></span>
                    </div>
                </div>
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

<div id="edit_review_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content">
            <div class="modal_header">
                <h3>리뷰 수정</h3>
                <button class="modal_close" onclick="close_edit_modal()">&times;</button>
            </div>
            <div class="modal_body">
                <form id="review_edit_form">
                    <input type="hidden" id="edit_review_id">
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
                    <div class="form_group">
                        <label for="edit_content">리뷰 내용 <span class="required">*</span></label>
                        <textarea id="edit_content" rows="8" placeholder="최소 20자 이상, 최대 1000자 이하로 작성해주세요" maxlength="1000" required></textarea>
                        <div class="char_count">
                            <span id="edit_char_count">0</span> / 1000자
                        </div>
                    </div>
                    <div class="error_message" id="edit_error_message"></div>
                    <div class="form_buttons">
                        <button type="button" class="btn_cancel" onclick="close_edit_modal()">취소</button>
                        <button type="submit" class="btn_submit">수정 완료</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>

<div id="delete_confirm_modal" style="display: none;">
    <div class="modal_overlay">
        <div class="modal_content modal_content_small">
            <div class="modal_header">
                <h3>리뷰 삭제</h3>
                <button class="modal_close" onclick="close_delete_confirm_modal()">&times;</button>
            </div>
            <div class="modal_body">
                <div class="confirm_message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>정말로 이 리뷰를 삭제하시겠습니까?</p>
                    <p class="confirm_desc">삭제된 리뷰는 복구할 수 없습니다.</p>
                </div>
                <div class="form_buttons">
                    <button type="button" class="btn_cancel" onclick="close_delete_confirm_modal()">취소</button>
                    <button type="button" class="btn_delete" id="confirm_delete_btn">삭제</button>
                </div>
            </div>
        </div>
    </div>
</div>

<script src="/wherehouse/js/review_board.js"></script>
</body>
</html>