package com.example.roulettebackend.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.roulettebackend.handler.ChatWebSocketHandler;

@RestController
public class HistoryController {

    @Autowired
    private ChatWebSocketHandler handler;

    @GetMapping("/history/download")
    public ResponseEntity<byte[]> downloadHistory() {
        List<List<String>> history = handler.getHistory();

        // ここでは簡単に TXT 形式で出力
        StringBuilder sb = new StringBuilder();
        for (List<String> round : history) {
            for (String line : round) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=roulette-history.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(data);
    }
    
    @GetMapping("/history/json")
    public List<List<String>> getHistoryJson() {
        return handler.getHistory();
    }
}