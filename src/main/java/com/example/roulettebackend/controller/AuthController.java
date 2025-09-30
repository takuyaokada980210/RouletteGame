package com.example.roulettebackend.controller;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.roulettebackend.dto.LoginRequest;
import com.example.roulettebackend.entity.User;
import com.example.roulettebackend.service.UserService;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // 新規登録
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.get("displayName");

        // === 入力チェック ===
        if (username == null || !username.matches("^[a-zA-Z0-9_-]+$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "ログインIDは英数字、ハイフン、アンダースコアのみ使用できます"));
        }
        if (password == null || !password.matches("^[a-zA-Z0-9_-]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "パスワードは8文字以上の英数字、ハイフン、アンダースコアのみ使用できます"));
        }

        // === 重複チェック ===
        if (userService.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "このログインIDはすでに使用されています"));
        }

        // === 登録処理 ===
        User newUser = userService.register(username, password, displayName);
        return ResponseEntity.ok(Map.of(
                "id", newUser.getId(),
                "username", newUser.getUsername(),
                "displayName", newUser.getDisplayName(),
                "credits", newUser.getCredits()
        ));
    }

    // ログイン
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
        return userService.login(req.getUsername(), req.getPassword())
                .map(u -> {
                    session.setAttribute("LOGIN_USER_ID", u.getId());

                    // === クレジットリセット処理 ===
                    LocalDateTime now = LocalDateTime.now();
                    int currentHour = now.getHour();
                    LocalDateTime lastReset = u.getLastResetTime();

                    // credits が 1000 未満（0 含む）の場合のみ判定
                    if (u.getCredits() < 1000) {
                        boolean shouldReset = false;

                        // 初回リセット、または最後のリセットから時間帯が変わった場合
                        if (lastReset == null) {
                            shouldReset = true;
                        } else if (lastReset.getHour() != currentHour
                                || !lastReset.toLocalDate().equals(now.toLocalDate())) {
                            shouldReset = true;
                        }

                        if (shouldReset) {
                            u.setCredits(1000);
                            u.setLastResetTime(now);
                            userService.save(u); // DBに反映
                        }
                    }

                    Map<String, Object> body = Map.of(
                            "username", u.getUsername(), // ログインID
                            "displayName", u.getDisplayName(), // 表示名
                            "credits", u.getCredits());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> {
                    Map<String, Object> err = Map.of("error", "IDまたはパスワードが違います");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
                });
    }

    // ログアウト
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "ログアウトしました"));
    }
}