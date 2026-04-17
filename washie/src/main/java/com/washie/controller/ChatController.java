package com.washie.controller;

import com.washie.engine.ChatEngine;
import com.washie.model.ChatLog;
import com.washie.model.Layanan;
import com.washie.service.ChatService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.springframework.stereotype.Controller;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Controller
public class ChatController implements Initializable {

    @FXML private Label lblUserName;
    @FXML private VBox vboxHistory;
    @FXML private VBox vboxMessages;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField tfInput;
    @FXML private Button btnSend;

    // Quick action buttons
    @FXML private Button btnQuickLayanan;
    @FXML private Button btnQuickStatus;
    @FXML private Button btnQuickJam;
    @FXML private Button btnQuickLokasi;

    @FXML private VBox vboxWelcome;

    private String currentSessionId;
    private String currentJudul;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH.mm");

    private final ChatEngine chatEngine;
    private final ChatService chatService;
    private final SessionManager sessionManager;
    private final SceneManager sceneManager;

    public ChatController(ChatEngine chatEngine, ChatService chatService,
                          SessionManager sessionManager, SceneManager sceneManager) {
        this.chatEngine = chatEngine;
        this.chatService = chatService;
        this.sessionManager = sessionManager;
        this.sceneManager = sceneManager;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblUserName.setText(sessionManager.getCurrentUser().getNamaLengkap());
        loadSesiList();
        startNewSession();

        tfInput.setOnAction(e -> handleSend());
        scrollPane.vvalueProperty().bind(vboxMessages.heightProperty());
    }

    private void startNewSession() {
        currentSessionId = chatService.generateSessionId();
        currentJudul = null;
        vboxMessages.getChildren().clear();
        showWelcome();
    }

    private void showWelcome() {
        String nama = sessionManager.getCurrentUser().getNamaLengkap().split(" ")[0];
        addBotMessage("Halo, " + nama + "! Selamat datang di Washie. 👋\nSaya siap membantu informasi laundry Anda. Pilih pertanyaan atau ketik langsung!");
    }

    @FXML
    private void handleBuatPercakapanBaru() {
        startNewSession();
    }

    @FXML
    private void handleCariPercakapan() {
        // Simple: reload history
        loadSesiList();
    }

    @FXML
    private void handleQuickLayanan() { sendMessage("Apa saja layanan yang tersedia?"); }

    @FXML
    private void handleQuickStatus() { sendMessage("Cek status pesanan"); }

    @FXML
    private void handleQuickJam() { sendMessage("Jam operasional laundry"); }

    @FXML
    private void handleQuickLokasi() { sendMessage("Lokasi laundry"); }

    @FXML
    private void handleSend() {
        String text = tfInput.getText().trim();
        if (!text.isEmpty()) {
            tfInput.clear();
            sendMessage(text);
        }
    }

    @FXML
    private void handleKeluar() {
        try {
            sessionManager.logout();
            sceneManager.switchTo("/com/washie/view/LoginView.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String text) {
        // Hide quick buttons after first message
        if (vboxWelcome != null) {
            vboxWelcome.setVisible(false);
            vboxWelcome.setManaged(false);
        }

        addUserMessage(text);

        // Set session title from first message
        if (currentJudul == null) {
            currentJudul = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        }

        // Save user message
        chatService.saveChatLog(sessionManager.getCurrentUser(), currentSessionId, currentJudul, "USER", text);

        // Process bot response
        ChatEngine.BotResponse response = chatEngine.process(text);

        if (response.getType() == ChatEngine.ResponseType.LAYANAN && response.getData() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Layanan> layananList = (List<Layanan>) response.getData();
            addLayananMessage(layananList);
        } else {
            addBotMessage(response.getText());
        }

        // Save bot response
        chatService.saveChatLog(sessionManager.getCurrentUser(), currentSessionId, currentJudul, "BOT", response.getText());

        // Refresh sidebar
        loadSesiList();
    }

    private void addUserMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setPadding(new Insets(4, 12, 4, 60));

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("bubble-user");

        Text msg = new Text(text);
        msg.setWrappingWidth(350);
        msg.getStyleClass().add("bubble-text");

        Label time = new Label(java.time.LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("bubble-time");
        time.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(msg, time);
        container.getChildren().add(bubble);
        vboxMessages.getChildren().add(container);
    }

    private void addBotMessage(String text) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(4, 60, 4, 12));

        Label avatar = new Label("🧺");
        avatar.getStyleClass().add("bot-avatar");

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("bubble-bot");

        Text msg = new Text(text);
        msg.setWrappingWidth(400);
        msg.getStyleClass().add("bubble-text");

        Label time = new Label(java.time.LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("bubble-time");

        bubble.getChildren().addAll(msg, time);
        container.getChildren().addAll(avatar, bubble);
        vboxMessages.getChildren().add(container);
    }

    private void addLayananMessage(List<Layanan> list) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(4, 60, 4, 12));

        Label avatar = new Label("🧺");
        avatar.getStyleClass().add("bot-avatar");

        VBox bubble = new VBox(8);
        bubble.getStyleClass().add("bubble-bot");

        Label header = new Label("Halo! Berikut daftar layanan laundry Washie!");
        header.getStyleClass().add("layanan-header");
        bubble.getChildren().add(header);

        for (Layanan l : list) {
            HBox row = new HBox();
            row.getStyleClass().add("layanan-row");
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setSpacing(0);

            VBox info = new VBox(2);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label nama = new Label(l.getNamaLayanan());
            nama.getStyleClass().add("layanan-nama");
            Label est  = new Label(l.getEstimasiWaktu());
            est.getStyleClass().add("layanan-estimasi");
            info.getChildren().addAll(nama, est);

            Label harga = new Label(String.format("Rp%.0f/kg", l.getHarga()));
            harga.getStyleClass().add("layanan-harga");

            row.getChildren().addAll(info, harga);
            bubble.getChildren().add(row);
        }

        Label time = new Label(java.time.LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("bubble-time");
        bubble.getChildren().add(time);

        container.getChildren().addAll(avatar, bubble);
        vboxMessages.getChildren().add(container);
    }

    private void loadSesiList() {
        vboxHistory.getChildren().clear();
        List<Map<String, String>> sessions = chatService.getSesiByUser(sessionManager.getCurrentUser());
        for (Map<String, String> sesi : sessions) {
            Label lbl = new Label(sesi.get("judul"));
            lbl.getStyleClass().add("history-item");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setWrapText(false);
            String sid = sesi.get("sessionId");
            lbl.setOnMouseClicked(e -> loadSession(sid));
            vboxHistory.getChildren().add(lbl);
        }
    }

    private void loadSession(String sessionId) {
        currentSessionId = sessionId;
        vboxMessages.getChildren().clear();
        List<ChatLog> logs = chatService.getChatBySession(sessionId);
        for (ChatLog log : logs) {
            if ("USER".equals(log.getPengirim())) {
                addUserMessage(log.getPesan());
            } else {
                addBotMessage(log.getPesan());
            }
        }
    }
}
