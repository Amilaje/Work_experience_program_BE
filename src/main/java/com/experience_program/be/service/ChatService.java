package com.experience_program.be.service;

import com.experience_program.be.entity.ChatSession;
import com.experience_program.be.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;

    @Autowired
    public ChatService(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    public Page<ChatSession> getChatSessions(Pageable pageable) {
        return chatSessionRepository.findAll(pageable);
    }

    public ChatSession getChatSessionDetails(String conversationId) {
        return chatSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("ID " + conversationId + "에 해당하는 대화 세션을 찾을 수 없습니다."));
    }

    @Transactional
    public void deleteChatSession(String conversationId) {
        if (!chatSessionRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("ID " + conversationId + "에 해당하는 대화 세션을 찾을 수 없습니다.");
        }
        chatSessionRepository.deleteById(conversationId);
    }
}
