package controller;

import com.washie.engine.ChatEngine;
import com.washie.engine.ChatEngine.BotResponse;
import com.washie.engine.ChatEngine.ChatSession;
import com.washie.model.ChatLog;
import com.washie.service.ChatService;
import com.washie.util.SceneManager;
import com.washie.util.SessionManager;
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

    private String currentSessionId;
    private String currentJudul;
    private ChatSession chatSession = new ChatSession();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH.mm");

    private final ChatEngine     chatEngine;
    private final ChatService    chatService;
    private final SessionManager sessionManager;
    private final SceneManager   sceneManager;

    public ChatController(ChatEngine chatEngine, ChatService chatService, SessionManager sessionManager, SceneManager sceneManager) {
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
        scrollPane.vvalueProperty().bind(vboxMessages.heightProperty());
        tfInput.setOnAction(e -> handleSend());
    }

    private void startNewSession() {
        currentSessionId = chatService.generateSessionId();
        currentJudul = null;
        chatSession = new ChatSession();
        vboxMessages.getChildren().clear();
        String nama = sessionManager.getCurrentUser().getNamaLengkap().split(" ")[0];
        addBotBubble("Halo, " + nama + "! Selamat datang di Washie.\n" +
                "Saya siap membantu informasi laundry Anda.\n\n" +
                "Contoh yang bisa kamu tanyakan:\n" +
                "- cuci setrika 2 kg\n" +
                "- berapa harga cuci kering\n" +
                "- berapa lama cuci bedcover\n" +
                "- apa itu dry cleaning\n" +
                "- info terbaru\n" +
                "- lokasi\n" +
                "- cek WS-001");
    }

    @FXML private void handleBuatPercakapanBaru() { startNewSession(); }

    @FXML
    private void handleSend() {
        String text = tfInput.getText().trim();
        if (!text.isEmpty()) { tfInput.clear(); kirim(text); }
    }

    @FXML
    private void handleKeluar() {
        try { sessionManager.logout(); sceneManager.switchTo("/com/washie/view/LoginView.fxml"); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void kirim(String text) {
        addUserBubble(text);
        if (currentJudul == null)
            currentJudul = text.length() > 35 ? text.substring(0, 35) + "..." : text;

        chatService.saveChatLog(sessionManager.getCurrentUser(), currentSessionId, currentJudul, "USER", text);

        BotResponse resp = chatEngine.process(text, chatSession);
        addBotBubble(resp.getText());

        chatService.saveChatLog(sessionManager.getCurrentUser(), currentSessionId, currentJudul, "BOT", resp.getText());
        loadSesiList();
    }

    private void addUserBubble(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 12, 4, 60));
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("bubble-user");
        Text msg = new Text(text);
        msg.setWrappingWidth(360);
        msg.getStyleClass().add("bubble-text");
        Label time = new Label(java.time.LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("bubble-time");
        time.setMaxWidth(Double.MAX_VALUE);
        time.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().addAll(msg, time);
        row.getChildren().add(bubble);
        vboxMessages.getChildren().add(row);
    }

    private void addBotBubble(String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 60, 4, 12));
        Label avatar = new Label("W");
        avatar.getStyleClass().add("bot-avatar");
        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("bubble-bot");
        Text msg = new Text(text);
        msg.setWrappingWidth(420);
        msg.getStyleClass().add("bubble-text");
        Label time = new Label(java.time.LocalTime.now().format(TIME_FMT));
        time.getStyleClass().add("bubble-time");
        bubble.getChildren().addAll(msg, time);
        row.getChildren().addAll(avatar, bubble);
        vboxMessages.getChildren().add(row);
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
        chatSession      = new ChatSession();
        vboxMessages.getChildren().clear();
        List<ChatLog> logs = chatService.getChatBySession(sessionId);
        for (ChatLog log : logs) {
            if ("USER".equals(log.getPengirim())) addUserBubble(log.getPesan());
            else addBotBubble(log.getPesan());
        }
    }
}
