<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>WhereHouse - 리뷰 게시판</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        /* ========== Global Styles ========== */
        :root {
            --primary-color: #3b82f6;
            --sidebar-width: 480px;
            --header-height: 60px;
        }

        body {
            font-family: 'Noto Sans KR', sans-serif;
            margin: 0;
            padding: 0;
            background-color: #f8f9fa;
            color: #333;
            overflow-x: hidden;
        }

        /* ========== Sidebar (Filter) ========== */
        #information {
            position: fixed;
            top: 0;
            left: -480px; /* 초기 상태: 숨김 */
            width: var(--sidebar-width);
            height: 100vh;
            background: #fff;
            box-shadow: 2px 0 10px rgba(0,0,0,0.1);
            z-index: 1000;
            transition: left 0.3s ease;
            padding: 20px;
            box-sizing: border-box;
            overflow-y: auto;
        }

        .sidebar_header {
            font-size: 1.5rem;
            font-weight: bold;
            margin-bottom: 20px;
            border-bottom: 2px solid #eee;
            padding-bottom: 10px;
        }

        .filter_group {
            margin-bottom: 20px;
            position: relative; /* 자동완성 리스트 위치 기준 */
        }

        .filter_label {
            display: block;
            font-weight: bold;
            margin-bottom: 8px;
        }

        .filter_input, .filter_select {
            width: 100%;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 4px;
            box-sizing: border-box;
        }

        #apply_filter_btn {
            width: 100%;
            padding: 12px;
            background: var(--primary-color);
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 1rem;
        }

        /* ========== Main Content ========== */
        #main_content {
            margin-left: 0;
            transition: margin-left 0.3s ease;
            padding: 20px;
            padding-top: 80px; /* 헤더 공간 확보 */
            max-width: 1200px;
            margin: 0 auto;
        }

        /* 사이드바 토글 버튼 */
        #btn {
            position: fixed;
            top: 20px;
            left: 20px;
            z-index: 1100;
            background: #fff;
            border: 1px solid #ddd;
            padding: 10px 15px;
            cursor: pointer;
            border-radius: 4px;
            transition: left 0.3s ease;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }

        /* ========== Top Bar (Search & Write) ========== */
        .top_bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            background: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.05);
        }

        .search_wrapper {
            display: flex;
            gap: 10px;
            flex-grow: 1;
            max-width: 600px;
        }

        #keyword_search {
            flex-grow: 1;
            padding: 10px;
            border: 1px solid #ddd;
            border-radius: 4px;
        }

        #clear_keyword {
            background: #6c757d;
            color: white;
            border: none;
            padding: 0 15px;
            border-radius: 4px;
            cursor: pointer;
        }

        #write_review_btn {
            background: #10b981;
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 4px;
            cursor: pointer;
            font-weight: bold;
        }

        /* ========== Review List ========== */
        #review_list_container {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }

        .review_card {
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            cursor: pointer;
            transition: transform 0.2s;
            border: 1px solid #eee;
        }

        .review_card:hover {
            transform: translateY(-5px);
            box-shadow: 0 5px 15px rgba(0,0,0,0.12);
        }

        .review_card_header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 10px;
            font-size: 0.9rem;
            color: #666;
        }

        .review_rating { color: #f59e0b; }

        .review_property_info {
            font-weight: bold;
            color: var(--primary-color);
            margin-top: 10px;
            font-size: 0.95rem;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .review_summary {
            margin: 10px 0;
            color: #444;
            display: -webkit-box;
            -webkit-line-clamp: 3;
            -webkit-box-orient: vertical;
            overflow: hidden;
            height: 4.5em; /* 3줄 높이 */
        }

        .tag {
            display: inline-block;
            background: #f1f3f5;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.8rem;
            color: #495057;
            margin-right: 5px;
            margin-bottom: 5px;
        }

        /* ========== Pagination ========== */
        #pagination_container {
            margin-top: 30px;
            display: flex;
            justify-content: center;
            gap: 10px;
            align-items: center;
        }

        .pagination_btn {
            padding: 8px 16px;
            background: white;
            border: 1px solid #ddd;
            cursor: pointer;
            border-radius: 4px;
        }
        .pagination_btn:disabled {
            background: #eee;
            cursor: not-allowed;
            color: #aaa;
        }

        /* ========== Modals ========== */
        .modal {
            display: none;
            position: fixed;
            z-index: 2000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(0,0,0,0.5);
            overflow: auto;
        }

        .modal_content {
            background-color: #fff;
            margin: 5% auto;
            padding: 30px;
            border-radius: 8px;
            width: 90%;
            max-width: 600px;
            position: relative;
        }

        .close_modal {
            position: absolute;
            top: 15px;
            right: 20px;
            font-size: 28px;
            font-weight: bold;
            color: #aaa;
            cursor: pointer;
        }

        /* 폼 스타일 */
        .form_group { margin-bottom: 20px; position: relative; }
        .form_group label { display: block; margin-bottom: 8px; font-weight: bold; }
        .form_group input, .form_group textarea, .form_group select {
            width: 100%;
            padding: 10px;
            border: 1px solid #ccc;
            border-radius: 4px;
            box-sizing: border-box;
        }
        .form_group textarea { height: 150px; resize: vertical; }

        .submit_btn {
            width: 100%;
            padding: 12px;
            background: var(--primary-color);
            color: white;
            border: none;
            border-radius: 4px;
            font-size: 1rem;
            cursor: pointer;
        }

        .char_count { text-align: right; font-size: 0.8rem; color: #888; margin-top: 5px; }
        .error_msg { color: #dc3545; font-size: 0.9rem; display: none; margin-top: 5px; }
        .error_msg.show { display: block; }

        /* ========== Autocomplete List ========== */
        .autocomplete_list {
            position: absolute;
            top: 100%;
            left: 0;
            right: 0;
            background: white;
            border: 1px solid #ddd;
            border-top: none;
            max-height: 200px;
            overflow-y: auto;
            z-index: 1500;
            list-style: none;
            padding: 0;
            margin: 0;
            display: none;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }

        .autocomplete_list li {
            padding: 10px;
            cursor: pointer;
            border-bottom: 1px solid #eee;
            display: flex;
            align-items: center;
        }

        .autocomplete_list li:hover { background-color: #f8f9fa; }

        .type_badge {
            font-size: 0.75rem;
            padding: 2px 6px;
            border-radius: 4px;
            margin-right: 8px;
            color: white;
        }
        .type_badge.charter { background-color: #28a745; } /* 전세 */
        .type_badge.monthly { background-color: #ffc107; color: #333; } /* 월세 */

    </style>
</head>
<body>

<button id="btn">▶</button>

<div id="information">
    <div class="sidebar_header">검색 필터</div>

    <div class="filter_group">
        <label class="filter_label">매물 이름</label>
        <input type="text" id="filter_property_name" class="filter_input" placeholder="아파트/오피스텔 이름" autocomplete="off">
        <ul id="filter_results" class="autocomplete_list"></ul>
    </div>

    <button id="apply_filter_btn">필터 적용</button>
</div>

<div id="main_content">
    <div class="top_bar">
        <div class="search_wrapper">
            <input type="text" id="keyword_search" placeholder="리뷰 내용 키워드 검색">
            <button id="clear_keyword">초기화</button>
        </div>
        <button id="write_review_btn"><i class="fas fa-pen"></i> 리뷰 작성</button>
    </div>

    <div style="margin-bottom: 15px; color: #666;">
        <span id="total_review_count">로딩 중...</span>
    </div>

    <div id="review_list_container">
    </div>

    <div id="pagination_container"></div>
</div>

<div id="write_review_modal" class="modal">
    <div class="modal_content">
        <span class="close_modal" onclick="close_write_modal()">&times;</span>
        <h2>새 리뷰 작성</h2>
        <form id="review_form">
            <div class="form_group">
                <label>매물 검색</label>
                <input type="text" id="input_property_name" placeholder="매물 이름을 2글자 이상 입력하세요" autocomplete="off">

                <input type="hidden" id="selected_property_id" name="propertyId">

                <ul id="modal_results" class="autocomplete_list"></ul>
            </div>

            <div class="form_group">
                <label>별점</label>
                <select id="input_rating">
                    <option value="5">⭐⭐⭐⭐⭐ (5점)</option>
                    <option value="4">⭐⭐⭐⭐ (4점)</option>
                    <option value="3">⭐⭐⭐ (3점)</option>
                    <option value="2">⭐⭐ (2점)</option>
                    <option value="1">⭐ (1점)</option>
                </select>
            </div>

            <div class="form_group">
                <label>내용</label>
                <textarea id="input_content" placeholder="솔직한 거주 후기를 작성해주세요 (최소 20자)"></textarea>
                <div class="char_count"><span id="current_char_count">0</span>자</div>
            </div>

            <div id="form_error_message" class="error_msg"></div>
            <button type="submit" class="submit_btn">작성 완료</button>
        </form>
    </div>
</div>

<div id="edit_review_modal" class="modal">
    <div class="modal_content">
        <span class="close_modal" onclick="close_edit_modal()">&times;</span>
        <h2>리뷰 수정</h2>
        <form id="review_edit_form">
            <input type="hidden" id="edit_review_id">

            <div class="form_group">
                <label>내용</label>
                <textarea id="edit_content"></textarea>
                <div class="char_count"><span id="edit_char_count">0</span>자</div>
            </div>

            <div id="edit_error_message" class="error_msg"></div>
            <button type="submit" class="submit_btn">수정 완료</button>
        </form>
    </div>
</div>

<div id="delete_confirm_modal" class="modal">
    <div class="modal_content" style="text-align: center; max-width: 400px;">
        <h3>정말 삭제하시겠습니까?</h3>
        <p>삭제된 리뷰는 복구할 수 없습니다.</p>
        <div style="margin-top: 20px; display: flex; gap: 10px; justify-content: center;">
            <button id="confirm_delete_btn" style="background: #dc3545; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer;">삭제</button>
            <button onclick="close_delete_confirm_modal()" style="background: #6c757d; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer;">취소</button>
        </div>
    </div>
</div>

<script src="/js/review_board.js"></script>
</body>
</html>