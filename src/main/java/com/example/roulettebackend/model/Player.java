package com.example.roulettebackend.model;

public class Player {
    private String username;     // DB用（ユニークキー）
    private String displayName;  // UI表示用
    private int credits = 1000; 
    private boolean hasBet = false;
    private boolean host = false; 

    // コンストラクタ
    public Player(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    // --- Getter/Setter ---
    public String getUsername() { 
        return username; 
    }

    public String getDisplayName() { 
        return displayName; 
    }

    public int getCredits() { 
        return credits; 
    }
    public void setCredits(int credits) { 
        this.credits = credits; 
    }

    public boolean getHasBet() { 
        return hasBet; 
    }
    public void setHasBet(boolean hasBet) { 
        this.hasBet = hasBet; 
    }

    public boolean isHost() { 
        return host; 
    }
    public void setHost(boolean host) { 
        this.host = host; 
    }
}