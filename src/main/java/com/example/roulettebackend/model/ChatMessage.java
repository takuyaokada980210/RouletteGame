package com.example.roulettebackend.model;

public class ChatMessage {
    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }

    private MessageType type;
    private String from;
    private String text;

    public ChatMessage() {}

    public ChatMessage(MessageType type, String from, String text) {
        this.type = type;
        this.from = from;
        this.text = text;
    }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}