package com.example.roulettebackend.model;

public class Bet {
    private String playerName;  // 誰がベットしたか（必要なら利用）
    private int amount;         // ベット額
    private String target;      // 赤/黒 や 数字などを表現

    public Bet() {}

    // ★ ChatWebSocketHandler で使うコンストラクタ
    public Bet(String target, int amount) {
        this.target = target;
        this.amount = amount;
    }

    // ★ プレイヤー名まで指定する場合に使うコンストラクタ
    public Bet(String playerName, int amount, String target) {
        this.playerName = playerName;
        this.amount = amount;
        this.target = target;
    }

    // --- Getter ---
    public String getPlayerName() { return playerName; }
    public int getAmount() { return amount; }
    public String getTarget() { return target; }
}