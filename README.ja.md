# Town AI

[한국어](README.md) | [English](README.en.md) | [日本語](README.ja.md)

[![CI](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/kwanmo7/town-ai/actions/workflows/ci.yml)

Town AIは、実際に訪れた地域の評価とメモを記録し、その経験に基づいて地域を比較する個人向け意思決定支援システムです。LINEでは会話形式で素早く登録・レポート照会ができ、Webでは地域、訪問記録、統計、AIレポートをまとめて管理できます。

V1とProduction移行は完了しています。現在の正規データストアはFirestore Standardで、生成したMarkdownレポートはGoogle Cloud Storageに保存します。

## 主な機能

- 自然言語と選択式スコアから訪問記録の下書きを生成し、確認後に保存
- Areaの登録・照会・更新・Soft Delete
- Visitの登録・照会・更新・Hard Delete
- 全体・Area別統計と評価項目別Top 5
- AREA・COMPARE・SUMMARY・ALL形式のAIレポート生成
- LINE照会で元データが変わっていない場合の既存レポート再利用、Markdownの表示・ダウンロード・削除
- LINE Flex MessageとRich Menuによる登録・レポート照会
- Firebase Googleログインと単一許可UIDによるWebアクセス制御
- LINE Webhook署名、Cloud Tasks OIDC、期限付きレポートリンクの検証
- GitHub ActionsによるCIとCloud BuildによるBackend・Frontend自動デプロイ

## アーキテクチャ

```text
Browser ── Firebase Hosting ── /api rewrite ──┐
                                             ▼
LINE ── Webhook ── Cloud Tasks ──────── Cloud Run Backend
                                             ├─ Firestore: メタデータと状態
                                             ├─ Cloud Storage: レポート本文
                                             └─ OpenAI Responses API
```

Web管理APIはFirebase ID Tokenを検証します。LINEの非同期処理は専用Service AccountのOIDC Tokenを検証し、LINE用レポートURLには有効期限付きHMAC署名を使用します。

## 技術スタック

| 領域 | 技術 |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Gradle |
| Frontend | React 19, TypeScript 5, Vite 6 |
| Data | Firestore Standard, Google Cloud Storage |
| AI | OpenAI Responses API |
| LINE | LINE Messaging API, Flex Message, Rich Menu |
| Auth | Firebase Authentication, Google Sign-In |
| Runtime | Cloud Run, Cloud Tasks, Firebase Hosting |
| CI/CD | GitHub Actions, Cloud Build, Developer Connect |

正確な依存バージョンは[Backend Build](backend/app/build.gradle)と[Frontend Package](frontend/package.json)を参照してください。

## ローカル実行

Java 25、Node.js 24.19.x、PowerShellが必要です。ProductionのGCP操作にはGoogle Cloud CLIも必要です。基本CRUDはProduction CredentialなしでFirestore Emulatorを利用できます。

```powershell
# Terminal 1: Firestore Emulator
./backend/scripts/local-firestore-start.ps1

# Terminal 2: Backend
./backend/scripts/local-backend-firestore.ps1

# 任意のテストデータ
./backend/scripts/local-seed.ps1

# Terminal 3: Frontend
cd frontend
npm install
npm run dev
```

Viteは`/api`をローカルBackendへProxyします。詳細は[Backendガイド](backend/README.md)と[ローカルFirestoreガイド](docs/007-local-firestore-emulator.md)を参照してください。

## 検証

```powershell
cd backend
./gradlew test javadoc

cd ../frontend
npm run lint
npm test
npm run build
npm run test:e2e
```

実際のOpenAI APIを使うPrompt評価は通常のテストとは分離されており、API利用料金が発生します。Production Cleanup ScriptはデフォルトでDry Runです。

## リポジトリと文書

- `backend/`: Spring Boot API、Firestore、LINE・AI・Report処理、運用Script
- `frontend/`: React管理画面、Firebase認証、Unit・E2E Test
- `docs/`: 要求事項、アーキテクチャ、API、デプロイ、移行、検証文書
- `linebotdesign/`: LINE Flex Message・Rich Menuの基準Asset

[プロジェクト構造](docs/000-project-structure.md)、[要求事項](docs/001-requirements.md)、[アーキテクチャ](docs/002-system-architecture.md)、[API設計](docs/004-api-design.md)、[デプロイ](docs/006-deployment-operations.md)から確認できます。[Local MySQL](docs/legacy/009-legacy-local-mysql-api-validation.md)と[Cloud SQL Production](docs/legacy/010-legacy-cloud-sql-production-validation.md)の検証文書は、履歴記録として`docs/legacy/`に保存します。

## Productionと状態

Production Webは[https://town-ai.web.app](https://town-ai.web.app)で提供されますが、管理機能は所有者のGoogleアカウントだけに制限されています。Secret、許可UID、LINE Credential、Production DataはRepositoryへCommitしません。

V1は完了しています。今後の改善候補と判断記録は[docs/999-v1-completion-v2-backlog.md](docs/999-v1-completion-v2-backlog.md)で管理します。
