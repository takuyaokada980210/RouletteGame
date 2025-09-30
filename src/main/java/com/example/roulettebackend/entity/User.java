package com.example.roulettebackend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id; // 主キー

	private String username; // ログインID
	private String password; // ハッシュ化済みのパスワード
	private String displayName;// 表示名（チャット用など）
	private int credits; // クレジット残高

	@Column(name = "last_reset_time")
	private LocalDateTime lastResetTime;

	public LocalDateTime getLastResetTime() {
		return lastResetTime;
	}

	public void setLastResetTime(LocalDateTime lastResetTime) {
		this.lastResetTime = lastResetTime;
	}

}