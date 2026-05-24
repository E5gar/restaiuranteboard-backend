package com.restaiuranteboard.backend.service.chat;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class CartChatNotifyService {

    private final SimpMessagingTemplate messagingTemplate;

    public CartChatNotifyService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notificarCarritoActualizado(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/carrito/" + userId.trim(), "carrito_actualizado");
    }
}
