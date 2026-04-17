package com.washie.service;

import com.washie.model.ChatLog;
import com.washie.model.User;
import com.washie.repository.ChatLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ChatService {

    private final ChatLogRepository chatLogRepository;

    public ChatService(ChatLogRepository chatLogRepository) {
        this.chatLogRepository = chatLogRepository;
    }

    public void saveChatLog(User user, String sessionId, String judul, String pengirim, String pesan) {
        ChatLog log = new ChatLog(user, sessionId, judul, pengirim, pesan);
        chatLogRepository.save(log);
    }

    public List<ChatLog> getChatBySession(String sessionId) {
        return chatLogRepository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    public List<Map<String, String>> getSesiByUser(User user) {
        List<Object[]> raw = chatLogRepository.findSessionsByUser(user);
        List<Map<String, String>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object[] row : raw) {
            String sid = (String) row[0];
            String judul = (String) row[1];
            if (seen.add(sid)) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("sessionId", sid);
                map.put("judul", judul != null ? judul : "Percakapan");
                result.add(map);
            }
        }
        return result;
    }

    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }
}