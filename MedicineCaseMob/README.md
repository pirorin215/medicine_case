# MedicineCaseMob

スマート薬ケース (Medicine Case) 用 Android アプリケーション

## 概要

このアプリは、BLE (Bluetooth Low Energy) を使用してスマート薬ケースと連携し、以下の機能を提供します。

- **服薬リマインダー**: 設定したスケジュール（朝・昼・夜）に基づき、飲み忘れを通知します。
- **履歴管理**: 薬ケースからの服薬データを自動記録し、カレンダー形式で確認できます。
- **デバイス設定**: 服薬検出の感度（角度）やクールダウン時間を変更できます。

## 技術スタック

- **言語**: Kotlin
- **UI**: Jetpack Compose
- **アーキテクチャ**: MVVM + Clean Architecture (DI: Hilt)
- **データベース**: Room
- **通信**: BLE (Android Bluetooth Stack)
- **非同期処理**: Coroutines + Flow
- **バックグラウンド処理**: Foreground Service + WorkManager

## 仕様ドキュメント

- **[システム仕様書](../docs/SYSTEM_SPEC.md)**: システム全体のアーキテクチャ、服薬検出仕様、通知・履歴管理仕様
- **[BLE通信プロトコル仕様](../medicine_case/PROTOCOL_SPEC.md)**: Service UUID, Characteristic UUID, コマンドフォーマットの詳細

## セットアップ

1. Android Studio (Hedgehog 以降推奨) を開く。
2. `MedicineCaseMob` ディレクトリをプロジェクトとしてインポート。
3. `local.properties` が必要に応じて生成されるのを待つ。
4. 実機（Bluetooth LE 対応）で実行。

## 注意事項

- BLE 通信を行うため、位置情報の権限（Android 11 以下）または付近のデバイスの権限（Android 12 以上）が必要です。
- バックグラウンドでの通知を安定させるため、バッテリー最適化の除外設定を推奨します。

## ライセンス
MIT License
