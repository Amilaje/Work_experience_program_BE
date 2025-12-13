package com.experience_program.be.controller;

import com.experience_program.be.entity.ChatSession;
import com.experience_program.be.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<Page<ChatSession>> getChatSessions(
            @PageableDefault(sort = "lastUpdatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ChatSession> sessions = chatService.getChatSessions(pageable);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{conversationId}")
    public ResponseEntity<ChatSession> getChatSessionDetails(@PathVariable String conversationId) {
        ChatSession sessionDetails = chatService.getChatSessionDetails(conversationId);
        return ResponseEntity.ok(sessionDetails);
    }

    @DeleteMapping("/sessions/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChatSession(@PathVariable String conversationId) {
        chatService.deleteChatSession(conversationId);
    }
}
