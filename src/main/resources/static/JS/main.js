let ws, username, selectedTarget = null;
let currentBetAmount = 0, currentCredits = 1000;
let selectedNumbers = [];
let selectedOutside = null; // 外側ベット選択を保持
let lastResult = null;       // サーバーから来た結果（数値）
let pendingHistory = null;   // 遅延させる履歴
let joined = false;        // 参加確定したか
let bgmStarted = false;    // BGMをもう開始したか
let audioUnlocked = false; // ブラウザのオートプレイ制限を解除できたか
let minBet = 100;
let isGameOver = false; 

// === 初期表示 ===
window.onload = () => {
  document.getElementById("loginModal").style.display = "flex";
};

// === モーダル制御 ===
document.getElementById("showRegisterModal").addEventListener("click", () => {
  document.getElementById("loginModal").style.display = "none";
  document.getElementById("registerModal").style.display = "flex";
});

document.getElementById("cancelRegister").addEventListener("click", () => {
  document.getElementById("registerModal").style.display = "none";
  document.getElementById("loginModal").style.display = "flex";
});

// === ログイン処理 ===
document.getElementById("loginBtn").addEventListener("click", async () => {
  const usernameInput = document.getElementById("loginUsername").value.trim();
  const password = document.getElementById("loginPassword").value.trim();

  if (!usernameInput || !password) {
    alert("IDとパスワードを入力してください");
    return;
  }

  const res = await fetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: usernameInput, password })
  });

  if (res.ok) {
    // 音声解錠（再生→即停止）
    const bgmEl = document.getElementById("bgm");
    try {
      await bgmEl.play();
      bgmEl.pause();
      bgmEl.currentTime = 0;
      audioUnlocked = true;
    } catch (e) {
      audioUnlocked = false; // 失敗してもOK。後で再挑戦か、トグルで再生可能
    }
}

  if (res.ok) {
      const data = await res.json();
      document.getElementById("loginModal").style.display = "none";

    // 表示名を右カラムに反映
    document.getElementById("loggedInUserLabel").innerText = "ユーザー名: " + data.displayName;

    // 所持クレジット反映
    currentCredits = data.credits;
	document.getElementById("currentCreditsDisplay").innerText = "$" + currentCredits;

    // グローバル変数 username に displayName を保持
	username = data.username;       // ログインID（例: testid）
	displayName = data.displayName; // 表示名（例: テストユーザー）

	  const bgmEl = document.getElementById("bgm");
	  if (audioUnlocked) {
		  bgmEl.play().then(() => {
			  bgmStarted = true;
			  console.log("✅ BGM started right after login");
		  }).catch(err => console.log("⚠️ BGM再生エラー:", err));
	  }

    // WebSocket接続
    joinChat();
  } else {
    alert("ログイン失敗: IDまたはパスワードが違います");
  }
});

// === 新規登録処理 ===
document.getElementById("registerBtn").addEventListener("click", async () => {
  const displayName = document.getElementById("registerDisplayName").value.trim();
  const usernameInput = document.getElementById("registerUsername").value.trim();
  const password = document.getElementById("registerPassword").value.trim();

  if (!displayName || !usernameInput || !password) {
    alert("すべての項目を入力してください");
    return;
  }

  const res = await fetch("/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName, username: usernameInput, password })
  });

  if (res.ok) {
    alert("登録完了しました。ログインしてください。");
    document.getElementById("registerModal").style.display = "none";
    document.getElementById("loginModal").style.display = "flex";
  } else {
    const err = await res.json().catch(() => null);
    if (err && err.error) {
      alert("登録失敗: " + err.error);
    } else {
      alert("登録失敗");
    }
  }
});

// === ログアウト処理 ===
function logout() {
  document.getElementById("logoutModal").style.display = "flex";
}

document.getElementById("confirmLogout").addEventListener("click", () => {
  fetch("/auth/logout", { method: "POST" }).catch(() => {});
  location.reload();
});

document.getElementById("cancelLogout").addEventListener("click", () => {
  document.getElementById("logoutModal").style.display = "none";
});


// === WebSocket接続 ===
function joinChat() {
    if (!username) return;
    ws = new WebSocket(
      (location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/chat"
    );

	ws.onopen = () => {
	    console.log("✅ WebSocket connected, sending join:", username);
	    ws.send("/join:" + username);
	};

	ws.onmessage = (event) => {
	  const data = event.data;
	  try {
	    const obj = JSON.parse(data);

	    // ルーレット開始
	    if (obj.type === "SPIN_START") {
	      const spinAudio = document.getElementById("spinSound");
	      spinAudio.currentTime = 0;
	      spinAudio.play().catch(()=>{});
	      animateRoulette();
	      return;
	    }

	    // 結果表示
	    if (obj.type === "SPIN_RESULT") {
	      lastResult = obj;

	      // ★3秒待ってから結果モーダルを表示
	      setTimeout(() => {
	        const { color, number, isWin, payout, credits } = obj;

	        document.getElementById("resultNumber").innerText = "出た数字: " + number;
	        document.getElementById("resultColor").innerText =
	          "色: " + (color === "red" ? "赤" : color === "black" ? "黒" : "緑");
	        document.getElementById("resultWinLose").innerText =
	          isWin ? `✅ 勝利！ +$${payout} (残高 $${credits})`
	                : `❌ 敗北... (残高 $${credits})`;

	        currentCredits = credits;
	        document.getElementById("currentCreditsDisplay").innerText = "$" + currentCredits;

	        const modal = document.getElementById("resultModal");
	        modal.style.display = "flex";

	        // OKボタンを押すとモーダルを閉じるだけ
			  const okBtn = modal.querySelector("button");
			  okBtn.onclick = () => {
				  modal.style.display = "none";
				  if (isGameOver) {
					  window.location.href = "/gameover.html";
				  }
	          // ★ここではページ遷移しない
	        };

	        if (pendingHistory) {
	          renderHistory(pendingHistory);
	          pendingHistory = null;
	        }

	        if (isWin) {
	          const fanfare = document.getElementById("fanfareSound");
	          fanfare.currentTime = 0;
	          fanfare.play().catch(()=>{});
	        }
	      }, 3000);
	      return;
	    }

	    // サーバーが敗退を通知してきたときのみページ遷移
            if (obj.type === "GAME_OVER") {
                console.log("サーバーGAME_OVER受信");
				isGameOver = true;
                // サーバーからのメッセージがあればアラートやモーダル表示してもOK
                if (obj.message) alert(obj.message);
                window.location.href = "/gameover.html";
                return;
            }

	    if (obj.type === "ERROR") {
	      alert(obj.message);
	      ws.close();
	      document.getElementById("loginModal").style.display = "flex";
	      return;
	    }

	    if (obj.type === "PLAYER_LIST") {
	      renderPlayerList(obj.players, obj.hostName);
	      if (obj.minBet) minBet = obj.minBet;
	      return;
	    }

	    if (obj.type === "HISTORY") {
	      pendingHistory = obj.history;
	      return;
	    }

	  } catch (e) {
	    handleTextMessage(data);
	  }
	};
}

function switchTab(tab) {
    document.querySelectorAll(".tab").forEach(t => t.classList.remove("active"));
    if (tab === "system") {
        document.querySelector(".tab-container .tab:nth-child(1)").classList.add("active");
        document.getElementById("system").style.display = "block";
        document.getElementById("chat").style.display = "none";
    } else {
        document.querySelector(".tab-container .tab:nth-child(2)").classList.add("active");
        document.getElementById("system").style.display = "none";
        document.getElementById("chat").style.display = "block";
    }
}

function handleTextMessage(data) {
    const system = document.getElementById("system");
    const chat = document.getElementById("chat");
	const systemPrefixes = [
	  "【入室】","【退出】","【敗退】",
	  "===","🎲","✅","❌","⚠️","➡","🔄","🚪","👑","🎉","😢","💰"
	];

    if (systemPrefixes.some(prefix => data.startsWith(prefix))) {
        system.innerHTML += "<div>" + data + "</div>";
        system.scrollTop = system.scrollHeight;
        if (data.startsWith("✅") || data.startsWith("🎉")) {
            const fanfare = document.getElementById("fanfareSound");
            fanfare.currentTime = 0; fanfare.play().catch(e => {});
        }
    } else if (!data.startsWith("/restart")) {
        chat.innerHTML += "<div>" + data + "</div>";
        chat.scrollTop = chat.scrollHeight;
    }
}

function sendMessage() {
    const msg = document.getElementById("message").value;
    if (ws && msg) { ws.send(msg); document.getElementById("message").value = ""; }
}

function spin() {
    if (!ws) return;

    // スピン音再生
    const spinAudio = document.getElementById("spinSound");
    spinAudio.currentTime = 0;
    spinAudio.play().catch(e => {});

    // サーバーにスピン要求
    ws.send("/spin");

    // ルーレット回転アニメーション開始（3秒）
    animateRoulette();
	}

// 回転アニメーション
function animateRoulette(onFinish) {
    let angle = 0;
    let speed = 0.2;  // 初速
    const deceleration = speed / (60 * 5); // 3秒で止まるように減速量計算

    function rotate() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        drawRoulette(angle);
        angle += speed;
        if (speed > 0) {
            speed -= deceleration;
            requestAnimationFrame(rotate);
        } else {
            if (onFinish) onFinish();
        }
    }
    rotate();
}

// === ログアウト処理 ===
function logout() {
  document.getElementById("logoutModal").style.display = "flex";
}

document.getElementById("confirmLogout").addEventListener("click", () => {
  // サーバー側にセッション削除を依頼（任意）
  fetch("/auth/logout", { method: "POST" }).catch(() => {});

  // ページをリロードしてログイン画面に戻る
  location.reload();
});

document.getElementById("cancelLogout").addEventListener("click", () => {
  document.getElementById("logoutModal").style.display = "none";
});

function restartGame() {
    if (ws) {
        ws.send("/restart");
    }
}

// === 修正版：プレイヤー一覧描画 ===
function renderPlayerList(players, hostName = null) {
    const div = document.getElementById("players");
    div.innerHTML = "";
    let allBet = true;

	players.forEach(p => {
	    let status = p.hasBet ? " ✅" : " ⏳";

	    const displayName = p.isHost ? "👑：" + p.name : p.name;
	    let highlight = "";

	    if (p.isHost) {
	        highlight = " style='background:lightyellow; font-weight:bold;'";
	    } else {
	        highlight = " style='background:rgba(135,206,250,0.8);'";
	    }

	    div.innerHTML += `<div${highlight}>${displayName} (残高:$${p.credits}) ${status}</div>`;

	    if (p.name === username) {
	        currentCredits = p.credits;
	        document.getElementById("currentCreditsDisplay").innerText = "$" + currentCredits;
	    }

	    if (!p.hasBet) allBet = false;
	});

    document.getElementById("spinButton").disabled = !allBet;

    const restartBtn = document.getElementById("restartButton");
    if (restartBtn) {
        restartBtn.disabled = (username !== hostName);
    }
}
function renderHistory(history) {
    const historyDiv = document.getElementById("history");
    historyDiv.innerHTML = "";

    for (let i = history.length - 1; i >= 0; i--) {
        let round = history[i];
        let card = document.createElement("div");
        card.className = "history-turn-card";

        round.forEach(item => {
            let entry = document.createElement("div");
            if (item.startsWith("===")) entry.className = "history-turn-title";
            if (item.startsWith("🎲")) entry.className = "history-result";
            if (item.startsWith("✅")) entry.className = "history-win";
            if (item.startsWith("❌")) entry.className = "history-lose";
            entry.innerText = item;
            card.appendChild(entry);
        });

        historyDiv.appendChild(card);
    }
}

function downloadHistory() {
    window.location.href = "/history/download";
}

function resetBet() {
    currentBetAmount = 0;
    document.getElementById("selectedBet").innerText = "$0";
}

// === 外側ベット選択 ===
function placeBetPreview(target) {
    if (currentBetAmount <= 0) {
        alert("先にベット額を決めてください。");
        return;
    }
    selectedOutside = target;
    selectedNumbers = [];
    document.querySelectorAll(".num-cell").forEach(cell => cell.classList.remove("num-selected"));
    updateSelectedBets();
}

function addChip(amount) {
    if (currentBetAmount + amount > currentCredits) {
        alert("所持クレジットを超えるため追加できません");
        return;
    }
    currentBetAmount += amount;
    document.getElementById("selectedBet").innerText = "$" + currentBetAmount;
}

const canvas = document.getElementById("rouletteCanvas");
const ctx = canvas.getContext("2d");
const centerX = canvas.width / 2;
const centerY = canvas.height / 2;
const radius = 240;

const numbers = [
    0, 32, 15, 19, 4, 21, 2, 25, 17, 34,
    6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
    24, 16, 33, 1, 20, 14, 31, 9, 22, 18,
    29, 7, 28, 12, 35, 3, 26
];

const rouletteImg = new Image();
rouletteImg.src = "/image/roulette.png";  // static 配下の画像は / から始めるのが確実

rouletteImg.onload = () => {
    drawRoulette(); // 画像が完全にロードされてから初回描画
};

function getColor(num) {
    if (num === 0) return "green";
    const reds = new Set([32,19,21,25,34,27,36,30,23,5,16,1,14,9,18,7,12,3]);
    return reds.has(num) ? "red" : "black";
}

const cellPositions = {};

function initRouletteTable() {
    const table = document.getElementById("rouletteTable");

    const zeroCell = document.createElement("div");
    zeroCell.className = "num-cell num-0";
    zeroCell.innerText = "0";
    zeroCell.dataset.num = 0;
    zeroCell.style.gridColumn = "3 / span 3";
    zeroCell.style.gridRow = "1";
    zeroCell.addEventListener("click", () => toggleNumber(0, zeroCell));
    table.appendChild(zeroCell);

    for (let row = 0; row < 12; row++) {
        for (let col = 0; col < 3; col++) {
            const num = row * 3 + (col + 1);
            const cell = document.createElement("div");
            cell.className = "num-cell " + (isRed(num) ? "num-red" : "num-black");
            cell.innerText = num;
            cell.dataset.num = num;
            cell.style.gridColumn = col + 3;
            cell.style.gridRow = row + 2;
            cell.addEventListener("click", () => toggleNumber(num, cell));
            table.appendChild(cell);
            cellPositions[num] = { row: row, col: col };
        }
    }

    for (let col = 0; col < 3; col++) {
        const outside = document.createElement("div");
        outside.className = "outside-cell";
        outside.innerText = "2 to 1";
        outside.style.gridColumn = col + 3;
        outside.style.gridRow = "14"; 
        outside.addEventListener("click", () => placeBetPreview("2to1_col" + (col + 1)));
        table.appendChild(outside);
    }
}

function initOutsideBetsVertical() {
    const table = document.getElementById("rouletteTable");
    const vertical = document.createElement("div");
    vertical.className = "outside-vertical";

    const bets = [
        { label: "EVEN", value: "EVEN" },
        { label: "RED", value: "RED", class: "red" },
        { label: "BLACK", value: "BLACK", class: "black" },
        { label: "ODD", value: "ODD" }
    ];

    bets.forEach(b => {
        const div = document.createElement("div");
        div.className = "outside-bet" + (b.class ? " " + b.class : "");
        div.innerText = b.label;
        div.onclick = () => placeBetPreview(b.value);
        vertical.appendChild(div);
    });

    table.appendChild(vertical);
}

function initOutsideBetsDozen() {
    const table = document.getElementById("rouletteTable");
    const dozen = document.createElement("div");
    dozen.className = "outside-dozen";

    [
        { label: "1st 12", value: "1st12" },
        { label: "2nd 12", value: "2nd12" },
        { label: "3rd 12", value: "3rd12" }
    ].forEach(b => {
        const div = document.createElement("div");
        div.className = "outside-bet";
        div.innerText = b.label;
        div.onclick = () => placeBetPreview(b.value);
        dozen.appendChild(div);
    });

    table.appendChild(dozen);
}

function initOutsideBetsHighLow() {
    const table = document.getElementById("rouletteTable");
    const highlow = document.createElement("div");
    highlow.className = "outside-highlow";

    const bets = [
        { label: "1 to 18", value: "1to18" },
        { label: "19 to 36", value: "19to36" }
    ];

    bets.forEach(b => {
        const div = document.createElement("div");
        div.className = "outside-bet";
        div.innerText = b.label;
        div.onclick = () => placeBetPreview(b.value);
        highlow.appendChild(div);
    });

    table.appendChild(highlow);
}

function isRed(num) {
    const reds = new Set([32,19,21,25,34,27,36,30,23,5,16,1,14,9,18,7,12,3]);
    return reds.has(num);
}

// === 数字セルのクリック処理 ===
function toggleNumber(num, cell) {
    if (currentBetAmount <= 0) {
        alert("先にベット額を決めてください。");
        return;
    }
    const idx = selectedNumbers.indexOf(num);
    if (idx >= 0) {
        selectedNumbers.splice(idx, 1);
        cell.classList.remove("num-selected");
    } else {
        selectedNumbers.push(num);
        cell.classList.add("num-selected");
    }
    selectedOutside = null;
    updateSelectedBets();
}

 // === ベット種別判定 ===
 function getBetType(nums) {
     if (nums.length === 1) return { name: "ストレートアップ", multiplier: 35 };
     if (nums.length === 2 && areAdjacent(nums[0], nums[1])) return { name: "スプリット", multiplier: 17 };
     if (nums.length === 3 && isStreet(nums)) return { name: "ストリート", multiplier: 11 };
     if (nums.length === 4 && isCorner(nums)) return { name: "コーナー", multiplier: 8 };
     if (nums.length === 6 && isLine(nums)) return { name: "ライン", multiplier: 5 };
     return { name: "不明な組み合わせ", multiplier: 0 };
 }

 // === 補助関数 ===
 function areAdjacent(n1, n2) {
     const a = cellPositions[n1], b = cellPositions[n2];
     return (a.row === b.row && Math.abs(a.col - b.col) === 1) ||
            (a.col === b.col && Math.abs(a.row - b.row) === 1);
 }

 function isStreet(nums) {
     const rows = nums.map(n => cellPositions[n].row);
     const cols = nums.map(n => cellPositions[n].col);
     return new Set(rows).size === 1 && new Set(cols).size === 3;
 }

 function isCorner(nums) {
     const rows = nums.map(n => cellPositions[n].row);
     const cols = nums.map(n => cellPositions[n].col);
     return new Set(rows).size === 2 && new Set(cols).size === 2;
 }

 function isLine(nums) {
     const rows = nums.map(n => cellPositions[n].row);
     const cols = nums.map(n => cellPositions[n].col);
     return new Set(rows).size === 2 && new Set(cols).size === 3;
 }

 // === 選択中のベット表示 & ボタン有効化 ===
 function updateSelectedBets() {
     const span = document.getElementById("selectedBets");
     const betButton = document.querySelector("button[onclick='confirmBet()']");

     if (selectedOutside) {
         // アウトサイドベット倍率
         let multiplier = 0;
         let label = selectedOutside;
         if (["RED","BLACK","EVEN","ODD","1to18","19to36"].includes(selectedOutside)) {
             multiplier = 2;
         } else if (["1st12","2nd12","3rd12"].includes(selectedOutside)) {
             multiplier = 3;
         } else if (selectedOutside.startsWith("2to1_col")) {
             multiplier = 3;
             label = "2 to 1";
         }
         const payout = currentBetAmount * multiplier;
         span.innerText = label + ` (${multiplier}倍：$${payout})`;
         betButton.disabled = (multiplier === 0);
     } else if (selectedNumbers.length > 0) {
         const { name, multiplier } = getBetType(selectedNumbers);
         const payout = currentBetAmount * multiplier;
         span.innerText = selectedNumbers.join(", ") + " → " + name + (multiplier > 0 ? ` (${multiplier}倍：$${payout})` : "");
         betButton.disabled = (multiplier === 0);
     } else {
         span.innerText = "なし";
         betButton.disabled = true;
     }
 }

 // === 選択解除 ===
 function resetBets() {
     selectedNumbers = [];
     selectedOutside = null;
     document.querySelectorAll(".num-cell").forEach(cell => cell.classList.remove("num-selected"));
     updateSelectedBets();
 }

 // === ベット確定 ===
 function confirmBet() {
     if (currentBetAmount <= 0) {
         alert("チップを選んでください");
         return;
     }
     let target = null;
     if (selectedOutside) {
         target = selectedOutside;
     } else if (selectedNumbers.length > 0) {
         const type = getBetType(selectedNumbers);
         if (type === "不明な組み合わせ") {
             alert("不明な組み合わせにはベットできません");
             return;
         }
         target = selectedNumbers.join("-");
     }
     if (!target) {
         alert("ベット対象を選んでください");
         return;
     }

     // サーバーに送信
     ws.send("/bet:" + target + ":" + currentBetAmount);

     // ★ 残高を即時反映（ベット額を引く）
     currentCredits -= currentBetAmount;
     document.getElementById("currentCreditsDisplay").innerText = "$" + currentCredits;

     // 選択リセット
     resetBet();
     resetBets();
 }

initOutsideBetsVertical();
initRouletteTable();
initOutsideBetsDozen();
initOutsideBetsHighLow();

function drawRoulette(angle = 0) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    ctx.save();
    ctx.translate(centerX, centerY);
    ctx.rotate(angle);  // 回転角度
    ctx.drawImage(rouletteImg, -radius, -radius, radius * 2, radius * 2);
    ctx.restore();

    // ↑マーカー（ゴールド三角）を重ねて描画
    ctx.beginPath();
    ctx.moveTo(centerX, centerY - radius - 10);
    ctx.lineTo(centerX - 10, centerY - radius - 30);
    ctx.lineTo(centerX + 10, centerY - radius - 30);
    ctx.closePath();
    ctx.fillStyle = "gold";
    ctx.fill();
}

// スピン処理（アニメーション）
function spinRoulette(targetNumber) {
    const sliceAngle = 2 * Math.PI / 37; // 欧米ルーレット37枠
    const targetIndex = numbers.indexOf(targetNumber);
    const targetAngle = -(targetIndex * sliceAngle);
    let angle = 0;
    let speed = 0.3;

    function animate() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        drawRoulette(angle);
        angle += speed;
        speed *= 0.99;

        if (speed > 0.01 || Math.abs((angle % (2 * Math.PI)) - targetAngle) > 0.05) {
            requestAnimationFrame(animate);
        } else {
            drawRoulette(targetAngle);
        }
    }
    animate();
	
	// === 結果モーダル表示 ===
	function showResultModal(number, color, isWin) {
	    const modal = document.getElementById("resultModal");

	    document.getElementById("resultNumber").innerText = "出た数字: " + number;
	    document.getElementById("resultColor").innerText = "色: " + 
	        (color === "red" ? "赤" : color === "black" ? "黒" : "緑");
	    document.getElementById("resultWinLose").innerText = isWin ? "✅ 勝利！" : "❌ 敗北...";

	    modal.style.display = "flex";

	    // fanfare 音声再生
	    const fanfare = document.getElementById("fanfareSound");
	    fanfare.currentTime = 0;
	    fanfare.play().catch(e => {});

	    // 5秒後に自動で閉じる
	    setTimeout(() => { closeResultModal(); }, 5000);
	}
	
	function getMinBet() {
	  return minBet;
	}

	function closeResultModal() {
	    document.getElementById("resultModal").style.display = "none";
	}			
}

// === サウンド関連 ===
const bgm = document.getElementById("bgm");
const spinSound = document.getElementById("spinSound");
const fanfareSound = document.getElementById("fanfareSound");

// デフォルト音量を 70%
bgm.volume = 0.2;
spinSound.volume = 0.4;
fanfareSound.volume = 0.4;

// BGM ON/OFF
document.getElementById("bgmToggle").addEventListener("change", (e) => {
  if (e.target.checked) {
    bgm.play().catch(e => {});
  } else {
    bgm.pause();
  }
});

// BGM 音量調整
document.getElementById("bgmVolume").addEventListener("input", (e) => {
  bgm.volume = parseFloat(e.target.value);
});

// SE ON/OFF
document.getElementById("seToggle").addEventListener("change", (e) => {
  const enabled = e.target.checked;
  spinSound.muted = !enabled;
  fanfareSound.muted = !enabled;
});

// SE 音量調整
document.getElementById("seVolume").addEventListener("input", (e) => {
  const vol = parseFloat(e.target.value);
  spinSound.volume = vol;
  fanfareSound.volume = vol;
});