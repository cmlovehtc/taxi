# Windows 電腦全自動更新題庫

警政署題庫下載服務會拒絕 GitHub 的海外雲端主機，因此這個專案改用一台位於台灣網路的 Windows 電腦執行下載。電腦完成一次設定後，不必開著瀏覽器；GitHub 會在每天台灣時間 04:20 自動執行。

## 最簡單安裝方式（建議）

1. 在 GitHub 開啟 [`點兩下安裝.cmd`](https://github.com/cmlovehtc/taxi/blob/main/%E9%BB%9E%E5%85%A9%E4%B8%8B%E5%AE%89%E8%A3%9D.cmd)，按右上方的 **Download raw file** 下載按鈕。
2. 在 Windows 的「下載」資料夾找到 `點兩下安裝.cmd`，點兩下執行。
3. Windows 詢問是否允許變更時按 **是**。
4. 安裝程式會自動開啟 GitHub Runner 頁面。在 **Configure** 區塊，按複製按鈕，複製包含 `config.cmd` 與 `--token` 的完整那一行。
5. 回到黑色安裝視窗，按任意鍵。程式會從剪貼簿讀取代碼、自動完成安裝，並在使用後清除剪貼簿。
6. 安裝完成後會開啟 Runners 頁面。看到電腦名稱顯示 **Idle** 就代表成功。

> GitHub 的設定代碼只有短時間有效。如果代碼曾貼到聊天室或其他公開位置，請不要再使用；等該組代碼產生滿一小時、確定過期後，再執行安裝檔取得新代碼。代碼只要在自己的電腦複製，不要再貼到聊天室。

如果 Windows 顯示「已保護您的電腦」，請先確認檔案確實是從自己的 `cmlovehtc/taxi` 專案下載，再按 **其他資訊 → 仍要執行**。不需要關閉防毒軟體或 Windows 安全性功能。

## 第一次測試

1. 開啟 [Windows 電腦自動更新官方題庫](https://github.com/cmlovehtc/taxi/actions/workflows/update-question-bank.yml)。
2. 按 **Run workflow → Run workflow**。
3. 電腦保持開機並連上網路，等待執行完成。
4. 出現綠色勾勾代表成功；題庫有變更時會自動提交到 `main`，沒有變更時也會正常完成。

## 平常使用

- Windows 可以鎖定，但不能關機、休眠或斷網。
- GitHub Runner 會安裝成 Windows 背景服務，重新開機後自動啟動，不必一直開著黑色視窗。
- 每天台灣時間 04:20 自動檢查警政署題庫。
- 下載或題數驗證失敗時，不會覆蓋目前正常的題庫。
- ChatGPT Site 會優先讀取 GitHub 上的最新題庫，不需重新部署網站。

## 手動安裝（只有簡單安裝失敗時才需要）

1. 用要長期執行更新的 Windows 電腦，登入 GitHub。
2. 開啟專案的 [Settings → Actions → Runners](https://github.com/cmlovehtc/taxi/settings/actions/runners)。
3. 按 **New self-hosted runner**，選擇 **Windows** 與 **x64**。
4. 從 Windows 開始功能表搜尋 **PowerShell**，按右鍵選擇「以系統管理員身分執行」。
5. 依 GitHub 畫面順序，逐行執行 **Download** 與 **Configure** 的命令，安裝在 `C:\actions-runner`。
6. 設定時選擇安裝成 Windows 服務。回到 Runners 頁面，顯示 **Idle** 就代表完成。

## 安全提醒

這是公開 GitHub 專案。不要核准陌生人提出的 Pull Request 工作流程，也不要把這台 Runner 提供給其他專案使用。
