package com.restaiuranteboard.backend.service.chat;

import com.restaiuranteboard.backend.model.nosql.ChatMessageEntry;
import com.restaiuranteboard.backend.model.nosql.ChatSession;
import com.restaiuranteboard.backend.model.nosql.ChatToolCallEntry;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ChatSessionRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatService {

    private static final int MAX_USER_MESSAGES = 3;
    private static final int MAX_TOOL_ROUNDS = 6;
    private static final String FALLBACK = "No puedo realizar ello.";

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final ChatContextBuilderService chatContextBuilderService;
    private final CerebrasApiService cerebrasApiService;
    private final ChatToolExecutorService chatToolExecutorService;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            UserRepository userRepository,
            ChatContextBuilderService chatContextBuilderService,
            CerebrasApiService cerebrasApiService,
            ChatToolExecutorService chatToolExecutorService
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.userRepository = userRepository;
        this.chatContextBuilderService = chatContextBuilderService;
        this.cerebrasApiService = cerebrasApiService;
        this.chatToolExecutorService = chatToolExecutorService;
    }

    public Map<String, Object> obtenerSesionActiva(User user, String chatType) {
        Optional<ChatSession> activa = chatSessionRepository.findFirstByUserIdAndChatTypeAndClosedFalseOrderByUpdatedAtDesc(
                user.getId().toString(),
                chatType
        );
        ChatSession session = activa.orElseGet(() -> crearSesion(user, chatType));
        return toSessionDto(session);
    }

    public Map<String, Object> nuevaSesion(User user, String chatType) {
        cerrarSesionesAbiertas(user, chatType);
        ChatSession session = crearSesion(user, chatType);
        return toSessionDto(session);
    }

    public List<Map<String, Object>> listarSesiones(User user, String chatType) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdAndChatTypeOrderByUpdatedAtDesc(
                user.getId().toString(),
                chatType
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatSession s : sessions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", s.getId());
            row.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            row.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
            row.put("closed", s.isClosed());
            row.put("userMessageCount", s.getUserMessageCount());
            row.put("maxUserMessages", MAX_USER_MESSAGES);
            row.put("preview", previewSesion(s));
            row.put("canContinue", !s.isClosed() && s.getUserMessageCount() < MAX_USER_MESSAGES);
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> obtenerSesionPorId(User user, String chatType, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Sesión no indicada.");
        }
        ChatSession session = chatSessionRepository.findById(sessionId.trim())
                .filter(s -> user.getId() != null && user.getId().toString().equals(s.getUserId()))
                .filter(s -> chatType.equals(s.getChatType()))
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada."));
        return toSessionDto(session);
    }

    public Map<String, Object> enviarMensaje(User user, String chatType, String sessionId, String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("Mensaje vacío.");
        }
        boolean admin = "ADMIN".equals(chatType);
        ChatSession session = resolverSesion(user, chatType, sessionId, true);
        if (session.isClosed() || session.getUserMessageCount() >= MAX_USER_MESSAGES) {
            session.setClosed(true);
            chatSessionRepository.save(session);
            return Map.of(
                    "sessionId", session.getId(),
                    "reply", "Has alcanzado el límite de 3 mensajes. Inicia un chat nuevo.",
                    "closed", true,
                    "sessionExpired", true,
                    "uiAction", ""
            );
        }
        int keyToUse = avanzarContador(session);
        chatSessionRepository.save(session);
        agregarMensaje(session, "USER", mensaje.trim(), null);
        session.setUserMessageCount(session.getUserMessageCount() + 1);
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);

        String systemPrompt = admin ? promptAdmin(user) : promptCliente(user);
        List<Map<String, Object>> tools = admin
                ? ChatToolDefinitions.herramientasAdmin()
                : ChatToolDefinitions.herramientasCliente();
        List<Map<String, Object>> llmMessages = construirMensajesLlm(session, systemPrompt);
        String reply;
        try {
            reply = ejecutarCicloLlm(session, keyToUse, llmMessages, tools, user, admin);
        } catch (Exception e) {
            reply = FALLBACK;
        }
        if (reply == null || reply.isBlank()) {
            reply = FALLBACK;
        }
        if (quiereAtencionCliente(mensaje, reply)) {
            agregarMensaje(session, "ASSISTANT", reply, null);
            session.setUpdatedAt(Instant.now());
            boolean cerrar = session.getUserMessageCount() >= MAX_USER_MESSAGES;
            session.setClosed(cerrar);
            chatSessionRepository.save(session);
            return Map.of(
                    "sessionId", session.getId(),
                    "reply", reply,
                    "closed", cerrar,
                    "sessionExpired", cerrar,
                    "uiAction", "ATENCION_CLIENTE",
                    "messages", historialUi(session)
            );
        }
        agregarMensaje(session, "ASSISTANT", reply, null);
        session.setUpdatedAt(Instant.now());
        boolean cerrar = session.getUserMessageCount() >= MAX_USER_MESSAGES;
        session.setClosed(cerrar);
        chatSessionRepository.save(session);
        return Map.of(
                "sessionId", session.getId(),
                "reply", reply,
                "closed", cerrar,
                "sessionExpired", cerrar,
                "uiAction", "",
                "messages", historialUi(session)
        );
    }

    private String ejecutarCicloLlm(
            ChatSession session,
            int keyIndex,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            User user,
            boolean admin
    ) {
        List<Map<String, Object>> working = new ArrayList<>(messages);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            CerebrasApiService.LlmResult result = cerebrasApiService.chatCompletion(keyIndex, working, tools);
            CerebrasApiService.LlmMessage msg = result.message();
            if (msg.toolCalls() == null || msg.toolCalls().isEmpty()) {
                return msg.content() == null ? FALLBACK : msg.content().trim();
            }
            working.add(cerebrasApiService.assistantToolCallsMessage(msg));
            List<ChatToolCallEntry> auditTools = new ArrayList<>();
            for (CerebrasApiService.ToolCallReq tc : msg.toolCalls()) {
                String toolResult = chatToolExecutorService.ejecutar(tc.name(), tc.arguments(), user, admin);
                ChatToolCallEntry entry = new ChatToolCallEntry();
                entry.setName(tc.name());
                entry.setArgumentsJson(tc.arguments());
                entry.setResultJson(toolResult);
                auditTools.add(entry);
                working.add(cerebrasApiService.toolMessage(tc.id(), toolResult));
            }
            agregarMensaje(session, "TOOL", "ejecucion", auditTools);
            session.setUpdatedAt(Instant.now());
            chatSessionRepository.save(session);
        }
        return FALLBACK;
    }

    private List<Map<String, Object>> construirMensajesLlm(ChatSession session, String systemPrompt) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatMessageEntry m : session.getMessages()) {
            if ("SYSTEM".equals(m.getSender())) {
                continue;
            }
            if ("USER".equals(m.getSender()) || "ASSISTANT".equals(m.getSender())) {
                out.add(Map.of("role", m.getSender().toLowerCase(Locale.ROOT), "content", m.getContent() == null ? "" : m.getContent()));
            }
        }
        return out;
    }

    private String promptCliente(User user) {
        String nombre = user.getFullName() == null ? "Cliente" : user.getFullName().split(" ")[0];
        return "Eres asistente del restaurante para " + nombre + ". Español breve Moneda Soles de Peru. "
                + chatContextBuilderService.bloqueContexto(user, false)
                + " Usa herramientas para carrito, catalogo y pedido. Max 10 unidades por producto. "
                + "Si no puedes cumplir la solicitud responde exactamente: " + FALLBACK + ". "
                + "Si reportan problema con pedido, entrega o pago, responde con empatia breve y sugiere el formulario de atencion al cliente (no intentes resolver el reclamo en chat).";
    }

    private String promptAdmin(User user) {
        return "Eres consultor del negocio para admin " + user.getFullName() + ". Español breve. "
                + chatContextBuilderService.bloqueContexto(user, true)
                + " Usa herramientas para KPIs, inventario, personal y correos. "
                + "Si no puedes cumplir responde exactamente: " + FALLBACK + ".";
    }

    private boolean quiereAtencionCliente(String userMsg, String reply) {
        String u = userMsg.toLowerCase(Locale.ROOT);
        if (u.contains("reportar") && u.contains("problema")) {
            return true;
        }
        if (u.contains("reclamo") || u.contains("queja")) {
            return true;
        }
        return u.contains("problema")
                && (u.contains("entrega") || u.contains("pedido") || u.contains("pago") || u.contains("repartidor"));
    }

    private ChatSession resolverSesion(User user, String chatType, String sessionId, boolean crearSiFalta) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> opt = chatSessionRepository.findById(sessionId.trim());
            if (opt.isPresent() && user.getId() != null && user.getId().toString().equals(opt.get().getUserId())) {
                return opt.get();
            }
        }
        Optional<ChatSession> activa = chatSessionRepository.findFirstByUserIdAndChatTypeAndClosedFalseOrderByUpdatedAtDesc(
                user.getId().toString(),
                chatType
        );
        if (activa.isPresent()) {
            return activa.get();
        }
        if (!crearSiFalta) {
            return crearSesion(user, chatType);
        }
        return crearSesion(user, chatType);
    }

    private ChatSession crearSesion(User user, String chatType) {
        ChatSession s = new ChatSession();
        s.setUserId(user.getId().toString());
        s.setUserEmail(user.getEmail());
        s.setUserRole(user.getRole() == null ? "" : user.getRole().getName());
        s.setChatType(chatType);
        s.setKeyCounter(1);
        s.setUserMessageCount(0);
        s.setClosed(false);
        ChatMessageEntry sys = new ChatMessageEntry();
        sys.setSender("SYSTEM");
        sys.setContent(chatType);
        s.getMessages().add(sys);
        return chatSessionRepository.save(s);
    }

    private void cerrarSesionesAbiertas(User user, String chatType) {
        chatSessionRepository.findFirstByUserIdAndChatTypeAndClosedFalseOrderByUpdatedAtDesc(
                user.getId().toString(),
                chatType
        ).ifPresent(s -> {
            s.setClosed(true);
            chatSessionRepository.save(s);
        });
    }

    private int avanzarContador(ChatSession session) {
        int next = session.getKeyCounter() >= 5 ? 1 : session.getKeyCounter() + 1;
        session.setKeyCounter(next);
        return next;
    }

    private void agregarMensaje(ChatSession session, String sender, String content, List<ChatToolCallEntry> tools) {
        ChatMessageEntry m = new ChatMessageEntry();
        m.setSender(sender);
        m.setContent(content);
        if (tools != null) {
            m.setToolCalls(tools);
        }
        m.setTimestamp(Instant.now());
        session.getMessages().add(m);
    }

    private String previewSesion(ChatSession session) {
        if (session.getMessages() == null) {
            return "Chat sin mensajes";
        }
        for (ChatMessageEntry m : session.getMessages()) {
            if ("USER".equals(m.getSender()) && m.getContent() != null && !m.getContent().isBlank()) {
                String c = m.getContent().trim();
                if (c.length() > 72) {
                    return c.substring(0, 72) + "...";
                }
                return c;
            }
        }
        for (int i = session.getMessages().size() - 1; i >= 0; i--) {
            ChatMessageEntry m = session.getMessages().get(i);
            if ("ASSISTANT".equals(m.getSender()) && m.getContent() != null && !m.getContent().isBlank()) {
                String c = m.getContent().trim();
                if (c.length() > 72) {
                    return c.substring(0, 72) + "...";
                }
                return c;
            }
        }
        return "Chat sin mensajes";
    }

    private Map<String, Object> toSessionDto(ChatSession session) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("sessionId", session.getId());
        dto.put("closed", session.isClosed());
        dto.put("userMessageCount", session.getUserMessageCount());
        dto.put("maxUserMessages", MAX_USER_MESSAGES);
        dto.put("canContinue", !session.isClosed() && session.getUserMessageCount() < MAX_USER_MESSAGES);
        dto.put("createdAt", session.getCreatedAt() == null ? null : session.getCreatedAt().toString());
        dto.put("updatedAt", session.getUpdatedAt() == null ? null : session.getUpdatedAt().toString());
        dto.put("messages", historialUi(session));
        return dto;
    }

    private List<Map<String, Object>> historialUi(ChatSession session) {
        List<Map<String, Object>> ui = new ArrayList<>();
        for (ChatMessageEntry m : session.getMessages()) {
            if ("SYSTEM".equals(m.getSender()) || "TOOL".equals(m.getSender())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sender", m.getSender());
            row.put("content", m.getContent());
            row.put("timestamp", m.getTimestamp() == null ? null : m.getTimestamp().toString());
            ui.add(row);
        }
        return ui;
    }

    public User requerirUsuarioPorEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        return user;
    }
}
