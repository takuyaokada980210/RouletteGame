package com.example.roulettebackend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import com.example.roulettebackend.model.ChatMessage;

@Controller
public class ChatController {

    @MessageMapping("/chat") // クライアント → /app/chat
    @SendTo("/topic/messages") // 全員へ配信
    public ChatMessage send(ChatMessage message) {
        // 通常チャットのとき
        message.setType(ChatMessage.MessageType.CHAT);
        return message;
    }
}