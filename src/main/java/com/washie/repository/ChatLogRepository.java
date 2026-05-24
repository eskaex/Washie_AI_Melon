package com.washie.repository;

import com.washie.model.ChatLog;
import com.washie.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    List<ChatLog> findByUserAndSessionIdOrderByTimestampAsc(User user, String sessionId);

    @Query("SELECT DISTINCT c.sessionId, c.judulPercakapan FROM ChatLog c WHERE c.user = :user ORDER BY c.timestamp DESC")
    List<Object[]> findSessionsByUser(@Param("user") User user);

    List<ChatLog> findBySessionIdOrderByTimestampAsc(String sessionId);
}