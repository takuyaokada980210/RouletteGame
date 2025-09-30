package com.example.roulettebackend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.roulettebackend.model.Bet;

@Service

//ルーレットのベットロジック

public class BetService {

    private static final Set<Integer> REDS = new HashSet<>(Arrays.asList(
        32,19,21,25,34,27,36,30,23,5,16,1,14,9,18,7,12,3
    ));

    
    //ベット展開処理 
    //例: "1-2-3" → Bet(1), Bet(2), Bet(3)
    public List<Bet> expandTarget(String target, int amount) {
        if (target.matches("^(\\d+)(-\\d+)+$")) {
            String[] parts = target.split("-");
            int n = parts.length;
            int base = amount / n;
            int rem = amount % n;
            List<Bet> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int a = base + (i < rem ? 1 : 0);
                list.add(new Bet(parts[i], a));
            }
            return list;
        }
        return Collections.singletonList(new Bet(target, amount));
    }

    
    //配当計算
    public int calculatePayout(Bet bet, int result, String color) {
        String target = bet.getTarget();
        int amount = bet.getAmount();

        // --- アウトサイドベット ---
        if (target.equals("赤") || target.equals("RED")) return color.equals("red") ? amount * 2 : 0;
        if (target.equals("黒") || target.equals("BLACK")) return color.equals("black") ? amount * 2 : 0;
        if (target.equals("奇数") || target.equals("ODD")) return (result % 2 == 1) ? amount * 2 : 0;
        if (target.equals("偶数") || target.equals("EVEN")) return (result != 0 && result % 2 == 0) ? amount * 2 : 0;
        if (target.equals("1to18")) return (1 <= result && result <= 18) ? amount * 2 : 0;
        if (target.equals("19to36")) return (19 <= result && result <= 36) ? amount * 2 : 0;
        if (target.equals("1st12")) return (1 <= result && result <= 12) ? amount * 3 : 0;
        if (target.equals("2nd12")) return (13 <= result && result <= 24) ? amount * 3 : 0;
        if (target.equals("3rd12")) return (25 <= result && result <= 36) ? amount * 3 : 0;
        if (target.startsWith("2to1_col")) {
            int col = Integer.parseInt(target.substring("2to1_col".length()));
            if ((result - col + 3) % 3 == 0 && result != 0) return amount * 3;
            return 0;
        }

        // --- インサイドベット ---
        if (target.matches("^[0-9]+(-[0-9]+)*$")) {
            String[] nums = target.split("-");
            Set<Integer> betNums = new HashSet<>();
            for (String n : nums) betNums.add(Integer.parseInt(n));

            if (!betNums.contains(result)) return 0;

            int size = betNums.size();
            if (size == 1) return amount * 36; // ストレート
            if (size == 2 && isSplit(betNums)) return amount * 18;
            if (size == 3 && isStreet(betNums)) return amount * 12;
            if (size == 4 && isCorner(betNums)) return amount * 9;
            if (size == 6 && isLine(betNums)) return amount * 6;
        }

        return 0;
    }

    // --- ヘルパーメソッド ---
    public String getColor(int num) {
        if (num == 0) return "green";
        return REDS.contains(num) ? "red" : "black";
    }

    private boolean isSplit(Set<Integer> nums) {
        if (nums.size() != 2) return false;
        List<Integer> list = new ArrayList<>(nums);
        int a = list.get(0), b = list.get(1);
        if (Math.abs(a - b) == 1 && (Math.min(a, b) % 3 != 0)) return true; // 横隣
        if (Math.abs(a - b) == 3) return true;                               // 縦隣
        return false;
    }

    private boolean isStreet(Set<Integer> nums) {
        if (nums.size() != 3) return false;
        List<Integer> list = new ArrayList<>(nums);
        list.sort(Integer::compare);
        int first = list.get(0);
        return list.equals(Arrays.asList(first, first+1, first+2)) && (first % 3 == 1);
    }

    private boolean isCorner(Set<Integer> nums) {
        if (nums.size() != 4) return false;
        List<Integer> list = new ArrayList<>(nums);
        list.sort(Integer::compare);
        int min = list.get(0);
        return list.containsAll(Arrays.asList(min, min+1, min+3, min+4)) && (min % 3 != 0);
    }

    private boolean isLine(Set<Integer> nums) {
        if (nums.size() != 6) return false;
        List<Integer> list = new ArrayList<>(nums);
        list.sort(Integer::compare);
        int first = list.get(0);
        return list.equals(Arrays.asList(first, first+1, first+2, first+3, first+4, first+5))
            && (first % 3 == 1);
    }
}