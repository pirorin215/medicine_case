# 服薬検知判定の改善プラン

## 課題

### 現状の問題

1. **10時10分に服薬（朝分）** → 朝に記録 ✓
2. **10時15分にケースを動かしただけ** → これも検知されて昼に記録される可能性 ✗

クールダウンは30秒なので、10分経過すれば再検知されます。

**根本原因**: マイコン側は「服薬したかどうか」しか判定していない

### 期待される動作

- 10時10分に服薬（朝分） → 朝に記録
- 10時15分に誤検知 → **無視**（同じ朝の時間帯なので）

## 解決策

### 基本方針

**「複雑な判定処理はスマホアプリ側で管理」**

- マイコン側はシンプルに：傾きを検知したらtimestampを記録するだけ
- スマホ側で「この検知は無効か有効か」を判定する

---

## 実装プラン

### Phase 1: スマホ側での重複検知フィルタリング

#### 1.1 判定ロジック

**ルール**: 最後の服薬から一定時間経過していない場合、無効とする

```kotlin
// 現在時刻が11:00、最後の服薬が10:10の場合
val lastIntakeTime = 今日の最後の服薬時刻  // 10:10
val currentTime = 今の時間                 // 11:00
val timeSinceLastIntake = currentTime - lastIntakeTime  // 50分

if (timeSinceLastIntake < MINIMUM_INTERVAL_HOURS) {
    // 無効: 同じ時間帯での連続検知とみなす
    return
}
// 有効: 十分な時間が経過しているので記録
```

#### 1.2 パラメータ

**MINIMUM_INTERVAL_HOURS = 2** （2時間）

- 理由: スケジュール間隔の最小値（朝7:00 → 昼12:00 = 5時間）
- 2時間以内の検知は「誤検知」とみなす

#### 1.3 時間帯の境界判定

**現状の判定**:
- 朝: 4:00 - 11:59
- 昼: 12:00 - 17:59
- 夜: 18:00 - 3:59

**改善案**: スマホの時刻ではなく、最後の服薬時刻で判定

```kotlin
val lastIntakeHour = getLastIntakeHour()  // 最後の服薬の時刻（時間）

val scheduleType = when (lastIntakeHour) {
    in 4..11 -> ScheduleType.MORNING
    in 12..17 -> ScheduleType.AFTERNOON
    else -> ScheduleType.EVENING
}
```

**なぜこれで良いか**:
- 10時10分に服薬 → 10時なので「朝」と判定
- 10時15分に誤検知 → 10時なので「朝」と判定 → 朝は既に記録済み → 無視
- 11時に服薬 → 11時なので「朝」と判定... これは問題

#### 1.4 改善された判定ロジック

**最後の服薬時刻と現在時刻の両方を考慮**

```kotlin
val lastIntakeTime = 今日の最後の服薬時刻
val currentTime = 今の時間
val hoursSinceLastIntake = (currentTime - lastIntakeTime) / 3600

if (hoursSinceLastIntake < 2) {
    // 2時間以内なら無視
    Log.d(TAG, "Ignoring intake: only ${hoursSinceLastIntake}h since last intake")
    return
}

// 2時間以上経過しているなら記録
val scheduleType = determineScheduleType(currentTime.hour)
recordIntake(scheduleType, currentTime)
```

**期待値**:
- 10:10に服薬（朝） → 記録
- 10:15に誤検知 → 無視（5分しか経っていない）
- 11:00に服薬 → 記録（55分経過だが...これは2時間以内なので無視されてしまう）

**問題**: 2時間ルールだと、11:00の服薬も無視されてしまう

---

## 改善案：スマートな間隔判定

### 方案A: 時間帯ベースの間隔

**ルール**: 同じ時間帯での2回目以降の検知は無視

```kotlin
val lastIntake = 今日の最後の服薬記録
val currentScheduleType = determineScheduleType(現在時刻)

if (lastIntake != null) {
    val lastScheduleType = lastIntake.scheduleType

    if (lastScheduleType == currentScheduleType) {
        // 同じ時間帯で既に記録済み → 無視
        Log.d(TAG, "Ignoring intake: already recorded for $currentScheduleType")
        return
    }
}

// 別の時間帯、または初回 → 記録
recordIntake(currentScheduleType, currentTime)
```

**期待値**:
- 10:10に服薬 → 朝に記録
- 10:15に誤検知 → 朝は既に記録済み → 無視 ✓
- 11:00に服薬 → 朝とは別の時間帯（11時は朝）... まだ朝なので無視 ✗

**問題**: 11時はまだ「朝」なので無視されてしまう

---

### 方案B: 最後の服薬からの絶対時間 + 時間帯考慮

**ルール**: どちらかの条件を満たす場合のみ記録

1. **時間条件**: 最後の服薬から **1時間以上** 経過している
2. **時間帯条件**: 最後の服薬と**異なる時間帯**

```kotlin
val lastIntake = 今日の最後の服薬記録
val currentTime = 今の時間

if (lastIntake != null) {
    val hoursSince = (currentTime - lastIntake.timestamp) / 3600
    val currentSchedule = determineScheduleType(currentTime.hour)
    val lastSchedule = determineScheduleType(lastIntake.timestamp.hour)

    val enoughTimePassed = hoursSince >= 1
    val differentSchedule = currentSchedule != lastSchedule

    if (!enoughTimePassed && !differentSchedule) {
        // 時間不足 & 同じ時間帯 → 無視
        Log.d(TAG, "Ignoring: only ${hoursSince}h since last, same schedule")
        return
    }
}

// 記録
recordIntake(currentSchedule, currentTime)
```

**期待値**:
- 10:10に服薬（朝） → 記録（初回）
- 10:15に誤検知 → 1時間未満 & 同じ時間帯（朝） → 無視 ✓
- 11:00に服薬 → 1時間未満だが... まだ朝なので無視 ✗

**まだ問題がある**: 11時はまだ「朝」なので無視される

---

### 方案C: 時間帯の境界を考慮した判定（推奨）

**ルール**: 時間帯の境界前後の余裕時間を設ける

```kotlin
val lastIntake = 今日の最後の服薬記録
val currentTime = 今の時間

if (lastIntake != null) {
    val minutesSince = (currentTime - lastIntake.timestamp) / 60

    // 最後の服薬から30分以内なら無視
    if (minutesSince < 30) {
        Log.d(TAG, "Ignoring: only ${minutesSince}m since last")
        return
    }

    // 同じ時間帯での記録済みかチェック
    val lastHour = lastIntake.timestamp.hour
    val currentHour = currentTime.hour
    val lastSchedule = determineScheduleType(lastHour)
    val currentSchedule = determineScheduleType(currentHour)

    if (lastSchedule == currentSchedule) {
        // 同じ時間帯 → 既に記録済みとみなして無視
        Log.d(TAG, "Ignoring: already recorded for $currentSchedule")
        return
    }
}

// 別の時間帯、または十分時間経過 → 記録
recordIntake(currentSchedule, currentTime)
```

**期待値**:
- 10:10に服薬（朝） → 記録
- 10:15に誤検知 → 5分しか経っていない & 同じ時間帯（朝） → 無視 ✓
- 11:00に服薬 → 50分経過しているが、まだ朝なので無視 ✗

**まだ問題がある**

---

### 方案D: スケジュール時刻を基準にした判定（最終推奨）

**ルール**: スケジュール時刻を基準として判定

```kotlin
val lastIntake = 今日の最後の服薬記録
val currentTime = 今の時間

// スケジュール時刻を取得
val morningTime = 朝のスケジュール時刻  // 例: 9:00
val afternoonTime = 昼のスケジュール時刻  // 例: 12:00
val eveningTime = 夜のスケジュール時刻    // 例: 19:00

if (lastIntake != null) {
    val minutesSince = (currentTime - lastIntake.timestamp) / 60

    // 最後の服薬から30分以内なら無視
    if (minutesSince < 30) {
        Log.d(TAG, "Ignoring: only ${minutesSince}m since last")
        return
    }

    // 最後の服薬がどのスケジュールに属するか判定
    val lastScheduleFor = determineScheduleForTime(lastIntake.timestamp, morningTime, afternoonTime, eveningTime)
    val currentScheduleFor = determineScheduleForTime(currentTime, morningTime, afternoonTime, eveningTime)

    if (lastScheduleFor == currentScheduleFor) {
        // 同じスケジュールの時間範囲 → 既に記録済みとみなして無視
        Log.d(TAG, "Ignoring: already recorded for $currentScheduleFor")
        return
    }
}

// 別のスケジュール時間範囲、または十分時間経過 → 記録
recordIntake(currentScheduleFor, currentTime)

// ヘルパー関数: スケジュール時刻を基準に判定
fun determineScheduleForTime(
    time: Long,
    morningTime: LocalTime,
    afternoonTime: LocalTime,
    eveningTime: LocalTime
): ScheduleType {
    val hour = time.hour
    val minute = time.minute

    return when {
        // 朝: 前日夜22:00 〜 昼12:00の30分前
        isAfterPreviousEvening(time, eveningTime) || hour < 12 && minute < 30 -> ScheduleType.MORNING

        // 昼: 12:00 〜 夜19:00の30分前
        hour >= 12 && (hour < 19 || (hour == 19 && minute < 30)) -> ScheduleType.AFTERNOON

        // 夜: 19:00 〜 翌朝の朝スケジュール時刻
        hour >= 19 || hour < morningTime.hour -> ScheduleType.EVENING
    }
}
```

**これでもまだ複雑すぎる**

---

## シンプルな解決策（推奨）

### 最終案: 30分ルール + 時間帯チェック

**ルール**: 以下の両方を満たす場合のみ記録

1. 最後の服薬から **30分以上** 経過している
2. スケジュール設定が **有効** である

```kotlin
fun processIntakeEvent(timestamp: Long) {
    val currentTime = System.currentTimeMillis() / 1000
    val todayRecord = getTodayRecord()
    val schedules = loadSchedules()

    // 最後の服薬からの経過時間を確認
    val lastIntakeTime = todayRecord?.lastIntakeTime() ?: 0L
    val minutesSince = (currentTime - lastIntakeTime) / 60

    if (minutesSince < 30) {
        Log.d(TAG, "Ignoring intake: only ${minutesSince}m since last intake")
        bleManager.clearIntake()
        return
    }

    // 現在時刻から時間帯を判定
    val hour = currentTime.hour
    val scheduleType = determineScheduleType(hour)
    val schedule = schedules.find { it.id == scheduleType.id }

    // スケジュールが有効か確認
    if (schedule?.enabled != true) {
        Log.d(TAG, "Ignoring intake: $scheduleType is disabled")
        bleManager.clearIntake()
        return
    }

    // 最後の記録と同じ時間帯なら無視
    val lastRecordedType = todayRecord?.lastRecordedType()
    if (lastRecordedType == scheduleType) {
        Log.d(TAG, "Ignoring intake: already recorded for $scheduleType")
        bleManager.clearIntake()
        return
    }

    // すべての条件をクリア → 記録
    recordIntake(scheduleType, currentTime)
    bleManager.clearIntake()
}
```

**期待値**:
- 10:10に服薬 → 朝が有効なら記録
- 10:15に誤検知 → 5分しか経っていない → 無視 ✓
- 11:00に服薬 → 50分経過 & 朝が有効 → 朝に記録 ✓
- 13:00に服薬 → 3時間経過 & 昼が有効 → 昼に記録 ✓

**メリット**:
- シンプルで実装しやすい
- 30分ルールで誤検知を防止
- 同じ時間帯での重複記録を防止

---

## 実装ステップ

### Step 1: MedicineIntakeRecord にフィールド追加

```kotlin
@Entity(tableName = "intake_records")
data class MedicineIntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,  // Unix timestamp (day precision)
    val morningTaken: Boolean = false,
    val morningTime: Long = 0L,
    val afternoonTaken: Boolean = false,
    val afternoonTime: Long = 0L,
    val eveningTaken: Boolean = false,
    val eveningTime: Long = 0L,
    val lastIntakeTimestamp: Long = 0L  // ← 追加：最後の服薬時刻
)
```

### Step 2: MainViewModel に判定ロジック追加

```kotlin
private fun recordIntakeLocally(mcuTimestamp: Long) {
    viewModelScope.launch {
        val now = Calendar.getInstance()
        val phoneTimestamp = System.currentTimeMillis() / 1000

        val todayRecord = repository.getIntakeRecordByDateSync(todayStart)
        val lastIntakeTime = todayRecord?.lastIntakeTimestamp ?: 0L
        val minutesSince = (phoneTimestamp - lastIntakeTime) / 60

        // 30分以内なら無視
        if (minutesSince < 30) {
            Log.d(TAG, "Ignoring intake: only ${minutesSince}m since last")
            bleManager.clearIntake()
            return@launch
        }

        val hour = now.get(Calendar.HOUR_OF_DAY)
        val scheduleType = determineScheduleType(hour)

        // 同じ時間帯が既に記録済みなら無視
        val alreadyTaken = when (scheduleType) {
            ScheduleType.MORNING -> todayRecord?.morningTaken == true
            ScheduleType.AFTERNOON -> todayRecord?.afternoonTaken == true
            ScheduleType.EVENING -> todayRecord?.eveningTaken == true
        }

        if (alreadyTaken) {
            Log.d(TAG, "Ignoring intake: already recorded for $scheduleType")
            bleManager.clearIntake()
            return@launch
        }

        // 記録
        val updatedRecord = (todayRecord ?: MedicineIntakeRecord(date = todayStart)).copy(
            lastIntakeTimestamp = phoneTimestamp,
            // ... 既存のロジック
        )

        repository.insertIntakeRecord(updatedRecord)
        bleManager.clearIntake()
    }
}
```

---

## まとめ

**推奨実装**: 30分ルール + 時間帯チェック

- シンプルで実装しやすい
- 期待通り動作する
- マイコン側は変更不要

**次のステップ**:
1. 実装する
2. テストする
3. 問題があれば調整
