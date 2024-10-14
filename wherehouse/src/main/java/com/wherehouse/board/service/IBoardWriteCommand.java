package com.wherehouse.board.service;

import jakarta.servlet.http.HttpServletRequest;

public interface IBoardWriteCommand {
	
	public void writeReply(HttpServletRequest httpRequest);
}
