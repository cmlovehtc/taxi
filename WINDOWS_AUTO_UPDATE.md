# Windows 電腦全自動更新題庫

警政署題庫下載服務會拒絕 GitHub 的海外雲端主機，因此這個專案改用一台位於台灣網路的 Windows 電腦執行下載。電腦完成一次設定後，不必開著瀏覽器；GitHub 會在每天台灣時間 04:20 自動執行。

## 第一次安裝

1. 用要長期執行更新的 Windows 電腦，登入 GitHub。
2. 開啟專案的 **Settings → Actions → Runners**：
   <https://github.com/cmlovehtc/taxi/settings/actions/runners>
3. 按 **New self-hosted runner**，選擇：
   - Runner image：**Windows**
   - Architecture：**x64**
4. 從 Windows 開始功能表搜尋 **PowerShell**，按右鍵選擇「以系統管理員身分執行」。
5. 依 GitHub 畫面顯示的順序，逐行執行 **Download** 與 **Configure** 區塊的命令。建議安裝在：

   ```text
   C:\actions-runner
   ```

6. 設定過程詢問是否安裝成 Windows 服務時，選擇 **Y**。其餘名稱及工作資料夾可直接按 Enter 使用預設值。
7. 回到 Runners 頁面。看到這台電腦顯示 **Idle**，就代表安裝完成。

> GitHub 畫面中的設定權杖只有短時間有效。請直接在自己的電腦執行，不要把權杖、密碼或兩步驟驗證碼貼到聊天室。

## 第一次測試

1. 開啟 [Windows 電腦自動更新官方題庫](https://github.com/cmlovehtc/taxi/actions/workflows/update-question-bank.yml)。
2. 按 **Run workflow → Run workflow**。
3. 電腦保持開機並連上網路，等待執行完成。
4. 出現綠色勾勾代表成功；題庫有變更時會自動提交到 `main`，沒有變更時也會正常完成。

## 平常使用

- Windows 可以鎖定，但不能關機、休眠或斷網。
- GitHub Runner 已安裝成 Windows 服務，重新開機後會自動啟動。
- 每天台灣時間 04:20 自動檢查警政署題庫。
- 下載或題數驗證失敗時，不會覆蓋目前正常的題庫。
- ChatGPT Site 會優先讀取 GitHub 上的最新題庫，不需重新部署網站。

## 安全提醒

這是公開 GitHub 專案。不要核准陌生人提出的 Pull Request 工作流程，也不要把這台 Runner 提供給其他專案使用。
