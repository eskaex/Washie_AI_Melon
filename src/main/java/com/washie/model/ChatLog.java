package com.washie.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_log")
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_User")
    private com.washie.model.User user;

    @Column(name = "sessionId")
    private String sessionId;

    @Column(name = "judulPercakapan")
    private String judulPercakapan;

    @Column(name = "pengirim")
    private String pengirim;

    @Column(name = "pesan", length = 2000)
    private String pesan;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public ChatLog() {}

    public ChatLog(com.washie.model.User user, String sessionId, String judulPercakapan, String pengirim, String pesan) {
        this.user = user;
        this.sessionId = sessionId;
        this.judulPercakapan = judulPercakapan;
        this.pengirim = pengirim;
        this.pesan = pesan;
    }

    @PrePersist
    protected void onCreate() { timestamp = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public com.washie.model.User getUser() { return user; }
    public void setUser(com.washie.model.User user) { this.user = user; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getJudulPercakapan() { return judulPercakapan; }
    public void setJudulPercakapan(String judulPercakapan) { this.judulPercakapan = judulPercakapan; }
    public String getPengirim() { return pengirim; }
    public void setPengirim(String pengirim) { this.pengirim = pengirim; }
    public String getPesan() { return pesan; }
    public void setPesan(String pesan) { this.pesan = pesan; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
