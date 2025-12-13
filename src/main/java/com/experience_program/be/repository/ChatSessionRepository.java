package com.experience_program.be.repository;

import com.experience_program.be.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    Optional<ChatSession> findFirstByOrderByLastUpdatedAtAsc();
}
