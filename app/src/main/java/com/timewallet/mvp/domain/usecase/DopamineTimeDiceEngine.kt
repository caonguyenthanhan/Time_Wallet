package com.timewallet.mvp.domain.usecase

import com.timewallet.mvp.domain.model.LedgerRecord
import com.timewallet.mvp.domain.model.TimeCategory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Trạng thái sinh học & hành vi hiện tại của người dùng, được tính từ dữ liệu Time Ledger trong ngày.
 */
enum class CognitiveState(
    val titleVi: String,
    val badgeColorHex: Long,
    val descriptionVi: String
) {
    HIGH_ENERGY_BUILDUP(
        titleVi = "Đầu chu kỳ · Cần kích hoạt Dopamine hành động",
        badgeColorHex = 0xFF1F8066L,
        descriptionVi = "Quỹ tập trung hôm nay còn dồi dào. Xúc xắc ưu tiên các phiên Pomodoro & Deep Work để tạo đà hưng phấn."
    ),
    ULTRADIAN_FLOW(
        titleVi = "Nhịp Ultradian · Cân bằng Tập trung & Phần thưởng",
        badgeColorHex = 0xFF287BCBL,
        descriptionVi = "Bạn đang trong nhịp làm việc tốt. Xúc xắc áp dụng lịch trình củng cố biến thiên (Variable Ratio) giữa làm việc và giải lao."
    ),
    FATIGUE_RECOVERY_NEEDED(
        titleVi = "Mỏi nhận thức · Ưu tiên phục hồi Dopamine",
        badgeColorHex = 0xFFCE9E54L,
        descriptionVi = "Bạn đã tập trung cường độ cao. Xúc xắc tự động giảm xác suất làm việc dài (2h) và tăng xác suất nghỉ ngơi để chống kiệt sức."
    ),
    DOPAMINE_RESET_GUARD(
        titleVi = "Cảnh báo lệch Dopamine · Cần quay lại nhịp làm việc",
        badgeColorHex = 0xFF8962C7L,
        descriptionVi = "Thời gian giải trí/nghỉ đang vượt trội so với làm việc. Xúc xắc tăng trọng số các phiên Pomodoro để tái thiết lập kỷ luật."
    )
}

enum class DiceActionKind(val labelVi: String) {
    WORK_FOCUS("Làm việc / Tập trung"),
    REWARD_REST("Nghỉ ngơi phục hồi"),
    REWARD_FUN("Giải trí có kiểm soát")
}

data class DiceFaceSpec(
    val faceNumber: Int, // 1..6
    val diceSymbol: String, // ⚀..⚅
    val headline: String,
    val activityTitle: String,
    val category: TimeCategory,
    val durationMinutes: Int,
    val actionKind: DiceActionKind,
    val scienceReason: String
) {
    val durationSeconds: Long
        get() = durationMinutes * 60L
}

data class DiceBehavioralContext(
    val todayFocusMinutes: Int,      // Tổng phút work + learn hôm nay
    val todayRestAndFunMinutes: Int, // Tổng phút rest + fun hôm nay
    val recentContinuousFocusMinutes: Int, // Số phút tập trung trong 3 giờ gần nhất
    val consecutiveBreakRolls: Int = 0,
    val preferredFocusCategory: TimeCategory = TimeCategory.WORK
)

data class FaceProbabilityItem(
    val face: DiceFaceSpec,
    val weight: Double,
    val probabilityPercent: Int
)

data class DiceRollResult(
    val selectedFace: DiceFaceSpec,
    val probabilityPercent: Int,
    val cognitiveState: CognitiveState,
    val allFaceProbabilities: List<FaceProbabilityItem>,
    val coachingNote: String
)

/**
 * Động cơ Xúc Xắc Thời Gian dựa trên Khoa học Hành vi Dopamine (Variable Ratio Reinforcement &
 * Reward Prediction Error) kết hợp Nhịp sinh học Pomodoro / Ultradian Rhythm (90–120 phút).
 */
object DopamineTimeDiceEngine {

    fun buildFaces(preferredFocusCategory: TimeCategory = TimeCategory.WORK): List<DiceFaceSpec> {
        val focusCat = if (preferredFocusCategory == TimeCategory.LEARN) TimeCategory.LEARN else TimeCategory.WORK
        val focusVerb = if (focusCat == TimeCategory.LEARN) "học tập" else "làm việc"

        return listOf(
            DiceFaceSpec(
                faceNumber = 1,
                diceSymbol = "⚀",
                headline = "Số 1: Pomodoro Khởi Động — Tập trung 25 phút!",
                activityTitle = "Pomodoro $focusVerb (25p)",
                category = focusCat,
                durationMinutes = 25,
                actionKind = DiceActionKind.WORK_FOCUS,
                scienceReason = "Hạ thấp ngưỡng kháng cự bắt đầu (Activation Energy). Cam kết 25 phút giúp não tiết Dopamine khởi động mà không gây ngợp."
            ),
            DiceFaceSpec(
                faceNumber = 2,
                diceSymbol = "⚁",
                headline = "Số 2: Thử thách Deep Work — Bạn phải $focusVerb 2 tiếng nữa!",
                activityTitle = "Deep Work $focusVerb sâu (2 giờ)",
                category = focusCat,
                durationMinutes = 120,
                actionKind = DiceActionKind.WORK_FOCUS,
                scienceReason = "Chu kỳ Ultradian 120 phút đưa não vào trạng thái Flow sâu nhất. Nỗ lực cao tạo Dopamine nội sinh (Effort-Driven Reward) bền vững."
            ),
            DiceFaceSpec(
                faceNumber = 3,
                diceSymbol = "⚂",
                headline = "Số 3: Phần thưởng phục hồi — Bạn được nghỉ 30 phút!",
                activityTitle = "Nghỉ ngơi tái tạo năng lượng (30p)",
                category = TimeCategory.REST,
                durationMinutes = 30,
                actionKind = DiceActionKind.REWARD_REST,
                scienceReason = "Reward Prediction Error (RPE > 0): Nghỉ 30 phút giúp mạng lưới thần kinh mặc định (DMN) củng cố trí nhớ và phục hồi thụ thể Dopamine."
            ),
            DiceFaceSpec(
                faceNumber = 4,
                diceSymbol = "⚃",
                headline = "Số 4: Pomodoro Kép — Bạn phải $focusVerb 50 phút nữa!",
                activityTitle = "Sprint $focusVerb (50p)",
                category = focusCat,
                durationMinutes = 50,
                actionKind = DiceActionKind.WORK_FOCUS,
                scienceReason = "Khung 50 phút tối ưu hóa độ tập trung trước khi vỏ não trước trán bắt đầu giảm hiệu suất ở mốc 60 phút."
            ),
            DiceFaceSpec(
                faceNumber = 5,
                diceSymbol = "⚄",
                headline = "Số 5: Giải trí có thưởng — Bạn được giải trí 45 phút!",
                activityTitle = "Giải trí có giới hạn (45p)",
                category = TimeCategory.FUN,
                durationMinutes = 45,
                actionKind = DiceActionKind.REWARD_FUN,
                scienceReason = "Củng cố tỷ lệ biến thiên (Variable Ratio Schedule): Giải trí có ranh giới thời gian rõ ràng giúp tận hưởng niềm vui mà không trượt vào Doomscrolling."
            ),
            DiceFaceSpec(
                faceNumber = 6,
                diceSymbol = "⚅",
                headline = "Số 6: Reset Sinh Học — Vận động nhẹ / Thư giãn 20 phút!",
                activityTitle = "Vận động & Reset Dopamine (20p)",
                category = TimeCategory.HEALTH,
                durationMinutes = 20,
                actionKind = DiceActionKind.REWARD_REST,
                scienceReason = "Thay đổi trạng thái sinh lý bằng vận động nhẹ 20 phút giúp đưa đường cơ sở Dopamine (Baseline) về mức cân bằng tự nhiên."
            )
        )
    }

    /**
     * Trích xuất ngữ cảnh hành vi từ danh sách bản ghi sổ cái hôm nay.
     */
    fun deriveContextFromRecords(
        todayDateLocal: String,
        records: List<LedgerRecord>,
        nowEpochMs: Long = System.currentTimeMillis(),
        consecutiveBreakRolls: Int = 0,
        preferredFocusCategory: TimeCategory = TimeCategory.WORK
    ): DiceBehavioralContext {
        val todayRecords = records.filter { it.date == todayDateLocal && it.layer.countsAsUsedTime }

        val focusSeconds = todayRecords
            .filter { it.category == TimeCategory.WORK || it.category == TimeCategory.LEARN }
            .sumOf { it.durationSeconds }

        val restFunSeconds = todayRecords
            .filter { it.category == TimeCategory.REST || it.category == TimeCategory.FUN }
            .sumOf { it.durationSeconds }

        val threeHoursAgoMs = nowEpochMs - 3 * 3_600_000L
        val recentFocusSeconds = todayRecords
            .filter {
                (it.category == TimeCategory.WORK || it.category == TimeCategory.LEARN) &&
                    it.endedAt != null && it.endedAt >= threeHoursAgoMs
            }
            .sumOf { record ->
                val start = max(record.startedAt ?: threeHoursAgoMs, threeHoursAgoMs)
                val end = record.endedAt ?: nowEpochMs
                max(0L, (end - start) / 1000L)
            }

        return DiceBehavioralContext(
            todayFocusMinutes = (focusSeconds / 60L).toInt(),
            todayRestAndFunMinutes = (restFunSeconds / 60L).toInt(),
            recentContinuousFocusMinutes = (recentFocusSeconds / 60L).toInt(),
            consecutiveBreakRolls = consecutiveBreakRolls,
            preferredFocusCategory = preferredFocusCategory
        )
    }

    fun determineCognitiveState(ctx: DiceBehavioralContext): CognitiveState {
        // 1. Nếu người dùng vừa lắc trúng nghỉ/giải trí >= 2 lần liên tiếp hoặc thời gian giải trí gấp đôi làm việc (> 45p)
        if (ctx.consecutiveBreakRolls >= 2 ||
            (ctx.todayRestAndFunMinutes >= 45 && ctx.todayRestAndFunMinutes > ctx.todayFocusMinutes * 2)
        ) {
            return CognitiveState.DOPAMINE_RESET_GUARD
        }

        // 2. Nếu vừa làm việc liên tục >= 90 phút (hết 1 chu kỳ Ultradian) hoặc tổng làm việc hôm nay >= 210 phút
        if (ctx.recentContinuousFocusMinutes >= 90 || ctx.todayFocusMinutes >= 210) {
            return CognitiveState.FATIGUE_RECOVERY_NEEDED
        }

        // 3. Nếu mới làm việc ít (< 45 phút trong ngày)
        if (ctx.todayFocusMinutes < 45) {
            return CognitiveState.HIGH_ENERGY_BUILDUP
        }

        // 4. Trạng thái cân bằng trong guồng Ultradian
        return CognitiveState.ULTRADIAN_FLOW
    }

    /**
     * Tính trọng số xác suất động cho 6 mặt xúc xắc dựa trên trạng thái não bộ & hành vi.
     */
    fun computeProbabilities(ctx: DiceBehavioralContext): Pair<CognitiveState, List<FaceProbabilityItem>> {
        val state = determineCognitiveState(ctx)
        val faces = buildFaces(ctx.preferredFocusCategory)

        // Trọng số tương ứng với mặt 1 (25p), mặt 2 (120p), mặt 3 (Nghỉ 30p), mặt 4 (50p), mặt 5 (Giải trí 45p), mặt 6 (Vận động 20p)
        val rawWeights: List<Double> = when (state) {
            CognitiveState.HIGH_ENERGY_BUILDUP -> listOf(
                28.0, // Mặt 1: Pomodoro 25p (dễ bắt đầu)
                18.0, // Mặt 2: Deep Work 2h (thử thách năng lượng đầu ngày)
                12.0, // Mặt 3: Nghỉ 30p (phần thưởng bất ngờ RPE)
                26.0, // Mặt 4: Sprint 50p
                8.0,  // Mặt 5: Giải trí 45p
                8.0   // Mặt 6: Vận động 20p
            )
            CognitiveState.ULTRADIAN_FLOW -> listOf(
                22.0, // Mặt 1: Pomodoro 25p
                14.0, // Mặt 2: Deep Work 2h
                20.0, // Mặt 3: Nghỉ 30p
                20.0, // Mặt 4: Sprint 50p
                12.0, // Mặt 5: Giải trí 45p
                12.0  // Mặt 6: Vận động 20p
            )
            CognitiveState.FATIGUE_RECOVERY_NEEDED -> listOf(
                18.0, // Mặt 1: Pomodoro ngắn 25p (nếu vẫn cần hoàn tất việc dở)
                5.0,  // Mặt 2: Deep Work 2h (giảm tối đa để chống kiệt sức/burnout!)
                32.0, // Mặt 3: Nghỉ 30p (ưu tiên cao nhất để hồi phục)
                9.0,  // Mặt 4: Sprint 50p
                18.0, // Mặt 5: Giải trí 45p (xả áp lực sau phiên dài)
                18.0  // Mặt 6: Vận động 20p (tái tạo tuần hoàn máu & Dopamine)
            )
            CognitiveState.DOPAMINE_RESET_GUARD -> listOf(
                32.0, // Mặt 1: Pomodoro 25p (kéo lại nhịp hành động ngay)
                20.0, // Mặt 2: Deep Work 2h
                8.0,  // Mặt 3: Nghỉ 30p (giảm mạnh vì đã nghỉ nhiều)
                28.0, // Mặt 4: Sprint 50p
                4.0,  // Mặt 5: Giải trí 45p (khóa bớt để tránh bẫy Dopamine)
                8.0   // Mặt 6: Vận động 20p
            )
        }

        val totalWeight = rawWeights.sum().coerceAtLeast(1.0)
        val items = faces.mapIndexed { index, face ->
            val w = rawWeights[index]
            FaceProbabilityItem(
                face = face,
                weight = w,
                probabilityPercent = ((w / totalWeight) * 100.0).roundToInt()
            )
        }
        return state to items
    }

    /**
     * Lắc xúc xắc theo phân phối xác suất có trọng số (Weighted Random Sampling).
     */
    fun roll(
        ctx: DiceBehavioralContext,
        randomDouble: Double = Random.nextDouble()
    ): DiceRollResult {
        val (state, probItems) = computeProbabilities(ctx)
        val totalWeight = probItems.sumOf { it.weight }
        val target = randomDouble.coerceIn(0.0, 0.999999) * totalWeight

        var cumulative = 0.0
        var chosen = probItems.last()
        for (item in probItems) {
            cumulative += item.weight
            if (target <= cumulative) {
                chosen = item
                break
            }
        }

        val focusPercentage = probItems
            .filter { it.face.actionKind == DiceActionKind.WORK_FOCUS }
            .sumOf { it.probabilityPercent }
        val breakPercentage = 100 - focusPercentage

        val coachingNote = buildString {
            append("Hôm nay đã tập trung ${ctx.todayFocusMinutes}p · Nghỉ/Giải trí ${ctx.todayRestAndFunMinutes}p. ")
            append("Tỷ lệ xúc xắc hiện tại: ${focusPercentage}% Làm việc / ${breakPercentage}% Nghỉ & Giải trí.")
        }

        return DiceRollResult(
            selectedFace = chosen.face,
            probabilityPercent = chosen.probabilityPercent,
            cognitiveState = state,
            allFaceProbabilities = probItems,
            coachingNote = coachingNote
        )
    }
}

