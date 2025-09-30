package com.example.roulettebackend.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.roulettebackend.entity.User;
import com.example.roulettebackend.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // コンストラクタでDI
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 新規登録
     * - パスワードは必ずハッシュ化して保存
     * - 初期クレジットは1000
     */
    public User register(String username, String rawPassword, String displayName) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword)) // ハッシュ化
                .displayName(displayName)
                .credits(1000)  // 初期残高
                .build();
        return userRepository.save(user);
    }

    /**
     * ログイン処理
     * - 入力された平文パスワードとDB保存済みのハッシュを比較
     */
    public Optional<User> login(String username, String rawPassword) {
        return userRepository.findByUsername(username)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword())); // ← ここ修正
    }

    /**
     * ユーザー情報を保存
     * - クレジット更新などにも利用
     */
    public User save(User user) {
        return userRepository.save(user);
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
}