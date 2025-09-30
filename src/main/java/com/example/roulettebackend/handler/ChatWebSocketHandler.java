package com.example.roulettebackend.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.roulettebackend.entity.User;
import com.example.roulettebackend.model.Bet;
import com.example.roulettebackend.model.Player;
import com.example.roulettebackend.repository.UserRepository;
import com.example.roulettebackend.service.BetService;
import com.fasterxml.jackson.databind.ObjectMapper; 

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

	private final ObjectMapper mapper = new ObjectMapper();
	private final Set<WebSocketSession> sessions = new HashSet<>();
	private final Map<String, String> sessionUsernameMap = new HashMap<>();
	private final Map<String, Player> playerMap = new HashMap<>();
	private final Map<String, List<Bet>> currentBets = new HashMap<>();
	private final List<List<String>> history = new ArrayList<>();
    private final BetService betService; 
    private final UserRepository userRepository;
    private final Set<String> forcedCloseSessions = new HashSet<>();
    
    public ChatWebSocketHandler(BetService betService, UserRepository userRepository) {
        this.betService = betService;
        this.userRepository = userRepository;
    }
	
	private int turnNumber = 1;
	private int minBet = 100;
	private static final int MAX_HISTORY_TURNS = 10;
	private String hostName = null;

	//private boolean gameOver = false;

	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
		sessionUsernameMap.put(session.getId(), "未設定");
	}
	
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        //　/join と /bet は prefix 付き
        if (payload.startsWith("/join:")) {
            handleJoin(session, payload);
            return;
        }
        if (payload.startsWith("/bet:")) {
            handleBet(session, payload);
            return;
        }

        switch (payload) {
            case "/spin":
                handleSpin(session);
                break;
            case "/quit":
                handleQuit(session);
                break;
            case "/restart":
                handleRestart(session);
                break;
            default:
                // 通常メッセージとして扱う
                String username = sessionUsernameMap.get(session.getId());
                if (username != null) {
                    broadcast(username + ": " + payload);
                }
                break;
        }
    }
    

    private void handleJoin(WebSocketSession session, String payload) throws Exception {
        String[] parts = payload.split(":", 2);
        String username = parts[1];

        // 🔒 同じログインIDで既に参加していないかチェック
        boolean alreadyLoggedIn = playerMap.values().stream()
                .anyMatch(p -> p.getUsername().equals(username));

        if (alreadyLoggedIn) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "ERROR");
            msg.put("message", "⚠️ このアカウントはすでにログイン中です");
            String json = mapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));

            forcedCloseSessions.add(session.getId()); // ★記録
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionUsernameMap.put(session.getId(), username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("ユーザーが存在しません"));

        // Playerインスタンスを作成
        Player newPlayer = new Player(user.getUsername(), user.getDisplayName());
        newPlayer.setCredits(user.getCredits());
        playerMap.put(session.getId(), newPlayer);

        // ホスト割り当て
        boolean noHostNow = (hostName == null) || playerMap.values().stream().noneMatch(Player::isHost);
        if (noHostNow) {
            playerMap.values().forEach(p -> p.setHost(false));
            newPlayer.setHost(true);
            hostName = username;
            broadcast("👑 新しいホストは " + user.getDisplayName() + " です");
        }

        // 入室通知
        broadcast("【入室】" + user.getDisplayName() + " が参加しました");
        broadcastPlayerList();
    }
    
    private void handleBet(WebSocketSession session, String payload) throws Exception {
        Player player = playerMap.get(session.getId());
        if (player == null) return;

        String[] parts = payload.split(":", 3);
        if (parts.length != 3) return;

        String target = parts[1];
        int amount = Integer.parseInt(parts[2]);

        if (amount >= minBet && player.getCredits() >= amount) {
            List<Bet> expanded = betService.expandTarget(target, amount);
            int total = expanded.stream().mapToInt(Bet::getAmount).sum();

            if (player.getCredits() < total) {
                session.sendMessage(new TextMessage("❌ 残高不足"));
                return;
            }

            player.setCredits(player.getCredits() - total);
            player.setHasBet(true);
            currentBets.computeIfAbsent(session.getId(), k -> new ArrayList<>()).addAll(expanded);

            broadcast("💰 " + player.getDisplayName() + " が " + target + " に $" + total + " をベット");
            broadcastPlayerList();
        } else {
            session.sendMessage(new TextMessage("❌ ベット失敗（最低額 $" + minBet + " / クレジット不足の可能性）"));
        }
    }
    
    private void handleSpin(WebSocketSession session) throws Exception {
        Player player = playerMap.get(session.getId());
        if (player == null) return;

        boolean allBet = playerMap.values().stream().allMatch(Player::getHasBet);
        if (!allBet) {
            session.sendMessage(new TextMessage("❌ まだ全員がベットしていません"));
            return;
        }

        // 全員に「スピン開始」を送信
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage("{\"type\":\"SPIN_START\"}"));
            }
        }

        int result = (int) (Math.random() * 37);
        String color = betService.getColor(result);

        List<String> roundHistory = new ArrayList<>();
        roundHistory.add("=== ターン " + turnNumber + " ===");
        roundHistory.add("🎲 ルーレット結果: " + result + " (" + color + ")");

        // 👇 追加：敗退予定者の一時リスト
        List<String> losers = new ArrayList<>();

        // 各プレイヤーごとの処理
        for (Map.Entry<String, List<Bet>> entry : currentBets.entrySet()) {
            Player p = playerMap.get(entry.getKey());
            if (p == null) continue;

            int winTotal = entry.getValue().stream()
                                .mapToInt(b -> betService.calculatePayout(b, result, color))
                                .sum();

            if (winTotal > 0) {
                p.setCredits(p.getCredits() + winTotal);
            }
            // DBへ残高を更新
            userRepository.findByUsername(p.getUsername()).ifPresent(user -> {
                user.setCredits(p.getCredits());
                userRepository.save(user);
            });

            roundHistory.add((winTotal > 0 ? "✅ " : "❌ ") + p.getDisplayName()
                    + (winTotal > 0 ? " 勝利 +$" + winTotal : " 敗北")
                    + " (残高 $" + p.getCredits() + ")");

            // SPIN_RESULT 個別通知
            WebSocketSession targetSession = sessions.stream()
                    .filter(s -> s.getId().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (targetSession != null && targetSession.isOpen()) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "SPIN_RESULT");
                msg.put("number", result);
                msg.put("color", color);
                msg.put("isWin", winTotal > 0);
                msg.put("payout", winTotal);
                msg.put("credits", p.getCredits());
                msg.put("minBet", minBet);
                targetSession.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
            }

            // 👇 敗退候補を記録（今は即通知しない）
            if (p.getCredits() < minBet) {
                losers.add(entry.getKey());
            }
        }

        // 履歴を更新・送信（結果モーダルと同時タイミング用）
        history.add(roundHistory);
        if (history.size() > MAX_HISTORY_TURNS) history.remove(0);
        broadcastHistory();

        // 各プレイヤーのベット状態をリセット
        playerMap.values().forEach(p -> p.setHasBet(false));
        currentBets.clear();

        // 👇 遅延で敗退通知＋次ターン案内を送信
        if (!losers.isEmpty()) {
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

                for (String id : losers) {
                    Player loser = playerMap.get(id);
                    if (loser != null) {
                        try {
                            broadcast("【敗退】" + loser.getDisplayName() + " が退場しました");
                        } catch (Exception ignored) {}

                        // 👑 ホストだった場合の権限移譲
                        boolean wasHost = loser.getUsername().equals(hostName);

                        playerMap.remove(id);
                        sessionUsernameMap.remove(id);

                        if (wasHost) {
                            if (playerMap.isEmpty()) {
                                hostName = null;
                            } else {
                                Player next = playerMap.values().iterator().next();
                                playerMap.values().forEach(p -> p.setHost(false));
                                next.setHost(true);
                                hostName = next.getUsername();
                                try {
                                    broadcast("👑 新しいホストは " + next.getDisplayName() + " です");
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                try {
                    broadcastPlayerList(); // リスト更新
                } catch (Exception ignored) {}

                // 次ターン更新
                turnNumber++;
                minBet += 50;
                try {
                    broadcast("➡ 次のターンへ（最低ベット $" + minBet + "）");
                    broadcastPlayerList();
                } catch (Exception ignored) {}
            }).start();
        } else {
            // 敗退者がいなければすぐ次ターン案内
            turnNumber++;
            minBet += 50;
            broadcast("➡ 次のターンへ（最低ベット $" + minBet + "）");
            broadcastPlayerList();
        }
    }

    private void handleQuit(WebSocketSession session) throws Exception {
        Player leaving = playerMap.remove(session.getId());
        if (leaving != null) {
            boolean wasHost = leaving.getUsername().equals(hostName);
            leaving.setHost(false); // 念のため

            broadcast("【退出】" + leaving.getDisplayName() + " が退出しました");
            sessionUsernameMap.remove(session.getId());
            sessions.remove(session);

            if (wasHost) {
                if (playerMap.isEmpty()) {
                    hostName = null; // 最後の1人が抜けたらホスト不在
                } else {
                    // 次のホストを指名
                    Player next = playerMap.values().iterator().next();
                    playerMap.values().forEach(p -> p.setHost(false));
                    next.setHost(true);
                    hostName = next.getUsername();
                    broadcast("👑 新しいホストは " + next.getDisplayName() + " です");
                }
            }
            broadcastPlayerList();
        }
    }
    
    private void handleRestart(WebSocketSession session) throws Exception {
        Player player = playerMap.get(session.getId());
        if (player != null && player.getUsername().equals(hostName)) {
            // リスタート処理
            turnNumber = 1;
            minBet = 100;
            history.clear();
            currentBets.clear();
            playerMap.values().forEach(p -> {
                p.setHasBet(false);
            });
            broadcast("{\"type\":\"HISTORY\",\"history\":[]}");
            broadcast("🔄 ゲームがリスタートしました！（最低ベット額が $100 に戻りました）");
            broadcastPlayerList();
        } else {
            session.sendMessage(new TextMessage("⚠️ リスタート権限がありません（ホストのみ可能）"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        if (forcedCloseSessions.remove(session.getId())) {
            // ★強制切断なので通知しない
            return;
        }

        sessions.remove(session);
        String username = sessionUsernameMap.remove(session.getId());
        Player removed = playerMap.remove(session.getId());
        if (username != null) {
            boolean wasHost = username.equals(hostName);
            if (removed != null) removed.setHost(false);

            broadcast("【退出】" + username + " が切断されました");

            if (wasHost) {
                if (playerMap.isEmpty()) {
                    hostName = null;
                } else {
                    Player next = playerMap.values().iterator().next();
                    playerMap.values().forEach(p -> p.setHost(false));
                    next.setHost(true);
                    hostName = next.getUsername();
                    broadcast("👑 新しいホストは " + next.getDisplayName() + " です");
                }
            }
            broadcastPlayerList();
        }
    }

	private void broadcast(String message) throws Exception {
		for (WebSocketSession s : sessions)
			if (s.isOpen())
				s.sendMessage(new TextMessage(message));
	}

	private void broadcastPlayerList() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<Map<String, Object>> players = new ArrayList<>();
		for (Player p : playerMap.values()) {
			Map<String, Object> map = new HashMap<>();
			map.put("name", p.getDisplayName());   // フロント表示用
			map.put("credits", p.getCredits());
			map.put("hasBet", p.getHasBet());
			map.put("isHost", p.isHost());
			players.add(map);
		}

		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "PLAYER_LIST");
		msg.put("players", players);
		msg.put("hostName", hostName);
        msg.put("minBet", minBet);
		
		String json = mapper.writeValueAsString(msg);
		for (WebSocketSession s : sessions) {
			if (s.isOpen()) {
				s.sendMessage(new TextMessage(json));
			}
		}
	}

	private void broadcastHistory() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(history);
		for (WebSocketSession s : sessions)
			if (s.isOpen())
				s.sendMessage(new TextMessage("{\"type\":\"HISTORY\",\"history\":" + json + "}"));
	}

	public List<List<String>> getHistory() {
		return history;
	}
}