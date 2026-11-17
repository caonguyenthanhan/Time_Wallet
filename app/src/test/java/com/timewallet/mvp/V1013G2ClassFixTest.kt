package com.timewallet.mvp

import com.timewallet.mvp.data.ai.GeminiProposalClient
import com.timewallet.mvp.domain.model.ActivityProposal
import com.timewallet.mvp.domain.usecase.AiProposalGuard
import com.timewallet.mvp.domain.usecase.CancelCommand
import com.timewallet.mvp.domain.usecase.CancelCommandParser
import com.timewallet.mvp.domain.usecase.OutcomeSource
import com.timewallet.mvp.domain.usecase.ReplyGuard
import com.timewallet.mvp.domain.usecase.VietnameseChatParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * WALLY v1.0.13, nhóm G2: ba LỚP lỗi phía ứng dụng làm chặn nhầm một câu trả lời ĐÚNG của AI.
 *  C1  Lệnh cục bộ nuốt câu "xóa/bỏ/hủy <việc ngoài ứng dụng> <thời lượng>" (quy tắc 7d của prompt: cloud ghi nó như một hoạt động).
 *  C2  Guard bỏ đề xuất đúng khi người dùng gõ KHÔNG DẤU hoặc viết "1h rưỡi".
 *  C3  ReplyGuard bỏ câu hỏi lại đúng khi nó nhắc giờ hiện tại, hai số "hay/hoặc" chung đơn vị, hoặc khoảng suy ra từ hai mốc giờ của người dùng.
 *
 * Câu mẫu là CÂU MỚI viết cho các lớp này (không lấy từ bộ holdout). Mọi test chạy mã sản xuất, không mạng, không khóa thật.
 * File này CHỈ dùng API đã có từ trước (chạy được trên bản gốc để chứng minh lỗi); test cần API mới nằm ở V1013G2InternalsTest.
 */
class V1013G2ClassFixTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val key = "sk-synthetic-key-123"

    // ------------------------------------------------------------------------------------------------ dùng chung
    private fun prop(title: String, minutes: Int, start: String? = null, end: String? = null, cat: String = "work") =
        V1012Rig.proposalJson(
            title = title, category = cat, durationSeconds = minutes * 60L, date = today.toString(), start = start, end = end,
            evidence = if (start != null && end != null) "explicit_range" else "duration_only"
        )

    private fun go(text: String, vararg ai: String, now: LocalTime = LocalTime.of(16, 30)) =
        V1012Rig.analyze(text, key, today, now, body = V1012Rig.chatBody("""{"reply":null,"proposals":[${ai.joinToString(",")}],"queries":[]}"""))

    // ================================================================================================ C1
    private val outsideApp = listOf(
        "Xóa hết ảnh trùng trong điện thoại 50 phút" to 50,
        "Bỏ cái bàn học cũ đi 40 phút" to 40,
        "Hủy tất cả lịch hẹn cũ 20 phút" to 20,
        "Xóa toàn bộ tin nhắn rác 15 phút" to 15,
        "Xóa ghi chép bài giảng cũ 1 tiếng" to 60,
        "Hủy ghi hình buổi livestream 30 phút" to 30,
        "Bỏ mục tiêu cũ trong sổ tay 30 phút" to 30,
        "Xóa hoạt động ngoại khóa cũ 45 phút" to 45,
        "Bỏ ghi âm bài giảng 35 phút" to 35,
        "Xóa hết bài tập cũ trong cặp 20 phút" to 20
    )

    @Test
    fun `C1 a duration sentence about something outside the app is not a local cancel command`() {
        for ((s, _) in outsideApp) assertNull("không phải lệnh hủy: $s", CancelCommandParser.parse(s))
    }

    @Test
    fun `C1 through the real client those sentences reach the cloud and come back as proposals`() {
        for ((s, minutes) in outsideApp) {
            val r = go(s, prop(s.substringBefore(" $minutes").take(40), minutes))
            assertEquals("phải gọi cloud đúng 1 lần: $s", 1, r.connections.size)
            assertEquals(s, OutcomeSource.CLOUD, r.outcome.source)
            assertEquals("$s -> ${r.outcome.warnings}", minutes, r.outcome.proposals.singleOrNull()?.durationMinutes)
        }
    }

    @Test
    fun `C1 sentences that clearly refer to the app's own drafts stay local commands and never reach the cloud`() {
        val local = listOf(
            "Hủy", "huỷ", "Hủy tất cả", "bỏ hết", "xóa hết đi nhé", "Hủy đề xuất", "Xóa tất cả đề xuất", "xóa hết đề xuất", "xóa toàn bộ bản nháp",
            "Hủy đề xuất số 3", "làm ơn xóa mục thứ 2", "Hủy ghi nhận #1", "Hủy nháp đọc sách 30 phút", "Bỏ bản ghi chạy bộ 25p",
            "Hủy ghi nhận 20 phút nấu ăn", "Bỏ ghi 15 phút dọn dẹp", "Hủy hoạt động đang chờ nấu ăn 20 phút", "Hãy hủy tất cả đề xuất đi nhé",
            "Xóa tất cả đề xuất học 2 tiếng", "xóa bản ghi hôm qua"
        )
        for (s in local) {
            assertNotNull("phải là lệnh cục bộ: $s", CancelCommandParser.parse(s))
            val r = go(s, prop("Học", 30))
            assertEquals("không được gọi cloud: $s", 0, r.connections.size)
            assertEquals(s, OutcomeSource.LOCAL, r.outcome.source)
            assertTrue(s, r.outcome.proposals.isEmpty())
        }
        assertEquals(CancelCommand.All, CancelCommandParser.parse("xóa hết đề xuất đọc sách 30 phút"))
        assertEquals(CancelCommand.Unspecified, CancelCommandParser.parse("Hủy"))
        assertEquals(CancelCommand.All, CancelCommandParser.parse("Xóa tất cả đề xuất"))
    }

    @Test
    fun `C1 the ViewModel asks the draft-aware parser only after the clear parser and inside the cancel branch`() {
        val vm = File("src/main/java/com/timewallet/mvp/ui/TimeWalletViewModel.kt").readText()
        val begin = vm.indexOf("AI-01 CLOSURE cancel branch begin")
        val end = vm.indexOf("AI-01 CLOSURE cancel branch end")
        assertTrue(begin > 0 && end > begin)
        assertTrue(vm.substring(begin, end).contains("CancelCommandParser.parse(text) ?: CancelCommandParser.parseDraftReference(text,"))
    }

    @Test
    fun `C1 nothing dangerous is loosened - no-duration forms, negations and cancel of recorded time are as before`() {
        // không có thời lượng: hành vi cũ (hỏi lại), không đổi
        assertNotNull(CancelCommandParser.parse("Xóa cái này đi"))
        assertNotNull(CancelCommandParser.parse("Hủy hoạt động học"))
        // mô tả hoạt động vốn đã đi lên cloud
        listOf("Hủy hợp đồng 2h", "Bỏ cuộc họp 1h", "Bỏ ghi chú 20 phút", "Xóa dữ liệu khách hàng 45p", "Gỡ cài đặt ứng dụng 10 phút").forEach {
            assertNull(it, CancelCommandParser.parse(it))
        }
        // lệnh hủy bản ghi đã lưu / dữ liệu: guard vẫn không cho đề xuất nào sống sót
        fun p(title: String, m: Int) = ActivityProposal(
            id = "x", rawInput = "x", title = title, category = "other", durationSeconds = m * 60L, dateLocal = today.toString(), startLocal = null,
            endLocal = null, timeEvidence = "duration_only", missingFields = listOf("startLocal", "endLocal")
        )
        listOf("Hủy ghi nhận 30 phút đọc sách", "Xóa bản ghi hôm qua", "Hủy đề xuất", "Xóa tất cả đề xuất", "Xóa bản ghi học 2h").forEach {
            assertTrue(it, AiProposalGuard.apply(it, listOf(p("Học", 30), p("Học", 120)), today, LocalTime.of(16, 30)).proposals.isEmpty())
        }
        // phủ định / chưa làm: vẫn không có đề xuất, kể cả khi đối tượng nằm ngoài ứng dụng
        listOf("Hôm nay tôi không xóa ảnh trùng 30 phút", "Chưa dọn tin nhắn rác 20 phút", "Đừng ghi 3 giờ làm việc", "Tôi không bỏ cái bàn cũ 40 phút").forEach {
            val r = go(it, prop("Dọn", 30), prop("Dọn", 20), prop("Làm việc", 180), prop("Bỏ bàn", 40))
            assertTrue("$it -> ${r.outcome.proposals.map { p -> p.title }}", r.outcome.proposals.isEmpty())
        }
    }

    // ================================================================================================ C2
    private fun kept(text: String, vararg ai: String, now: LocalTime = LocalTime.of(16, 30)): List<ActivityProposal> = go(text, *ai, now = now).outcome.proposals

    @Test
    fun `C2 unaccented sentences are verified like accented ones - the right proposal survives`() {
        val cases = listOf(
            Triple("Doc sach luc 8h toi 1 tieng ruoi", prop("Đọc sách", 90, "20:00", "21:30", "learn"), 90),
            Triple("Hoc luc 7h sang 3 tieng", prop("Học", 180, "07:00", "10:00", "learn"), 180),
            Triple("Lam bai tap tu 9h sang den 11h30", prop("Làm bài tập", 150, "09:00", "11:30", "learn"), 150),
            Triple("Tap gym tu 6h chieu den 7h30 toi", prop("Tập gym", 90, "18:00", "19:30", "health"), 90),
            Triple("Nau an 2 tieng ruoi", prop("Nấu ăn", 150, cat = "life"), 150),
            Triple("Chay bo 1h ruoi", prop("Chạy bộ", 90, cat = "health"), 90),
            Triple("Hop hai tieng ruoi", prop("Họp", 150), 150),
            Triple("Doc sach 45 phut", prop("Đọc sách", 45, cat = "learn"), 45)
        )
        for ((text, json, minutes) in cases) {
            val got = kept(text, json)
            assertEquals("$text phải giữ 1 đề xuất ($minutes phút)", minutes, got.singleOrNull()?.durationMinutes)
        }
    }

    @Test
    fun `C2 unaccented sentences still catch a wrong duration - the guard now really compares figures`() {
        assertTrue(kept("Nau an 2 tieng ruoi", prop("Nấu ăn", 120)).isEmpty())
        assertTrue(kept("Nau an 2 tieng ruoi", prop("Nấu ăn", 180)).isEmpty())
        assertTrue(kept("Hoc luc 7h sang 3 tieng", prop("Học", 120, "07:00", "09:00")).isEmpty())
        assertTrue(kept("Doc sach luc 8h toi 1 tieng ruoi", prop("Đọc sách", 60, "20:00", "21:00")).isEmpty())
        assertTrue(kept("Tap gym tu 6h chieu den 7h30 toi", prop("Tập gym", 60, "18:00", "19:00")).isEmpty())
        assertTrue(kept("Chay bo 1h ruoi", prop("Chạy bộ", 60)).isEmpty())
    }

    @Test
    fun `C2 half-hour forms after h, giờ and tiếng are 30 minutes more`() {
        fun n(s: String) = VietnameseChatParser.normalizeSpelledDurations(s)
        assertEquals("Dọn nhà 90 phút", n("Dọn nhà 1h rưỡi"))
        assertEquals("Nấu cơm 150 phút", n("Nấu cơm 2h rưỡi"))
        assertEquals("Nấu cơm 150 phút", n("Nấu cơm 2 giờ rưỡi"))
        assertEquals("Đọc 210 phút", n("Đọc 3 tiếng rưỡi"))
        assertEquals("Đọc 210 phút", n("Đọc ba tiếng rưỡi"))
        // sau từ/lúc/đến hoặc trước buổi, "2h rưỡi" là giờ đồng hồ 2h30, không phải 150 phút
        assertEquals("Họp lúc 2h30 chiều", n("Họp lúc 2h rưỡi chiều"))
        assertEquals("Họp 2h30 chiều", n("Họp 2h rưỡi chiều"))
        assertEquals(listOf(90), VietnameseChatParser.allDurationMinutes("Dọn nhà 1h rưỡi"))
        assertEquals(listOf(60), VietnameseChatParser.allDurationMinutes("Họp lúc 2h rưỡi chiều 1 tiếng"))
        // đề xuất qua guard
        assertEquals(150, kept("Học nhóm lúc 9h sáng 2h rưỡi", prop("Học nhóm", 150, "09:00", "11:30", "learn")).singleOrNull()?.durationMinutes)
        assertEquals(90, kept("Dọn nhà 1h rưỡi", prop("Dọn nhà", 90, cat = "life")).singleOrNull()?.durationMinutes)
        assertTrue("1h rưỡi không còn bị đọc là 60 phút", kept("Dọn nhà 1h rưỡi", prop("Dọn nhà", 60, cat = "life")).isEmpty())
        assertEquals(210, kept("Đọc sách 3 tiếng rưỡi", prop("Đọc sách", 210, cat = "learn")).singleOrNull()?.durationMinutes)
    }

    @Test
    fun `C2 the open-ended-to-now detector works without diacritics and keeps its limits`() {
        listOf("Chay bo tu 17h den bay gio", "Hoc tieng anh tu 19h toi gio", "Lam viec tu 9h den nay", "Doc sach tu 20h den gio roi", "Nau an tu 6h den luc nay", "Lam bai tu 9h den nay luon")
            .forEach { assertTrue(it, AiProposalGuard.hasOpenEndedToNow(it)) }
        listOf("Hoc tu 7h toi nay 2 tieng", "Di lam den gio lam viec", "Hoc tu 7h toi 9h", "Chay bo 30 phut").forEach { assertFalse(it, AiProposalGuard.hasOpenEndedToNow(it)) }
        assertEquals(setOf(17 * 60), AiProposalGuard.openEndedStartCandidates("Chay bo tu 17h den bay gio"))
        assertEquals(setOf(20 * 60 + 15), AiProposalGuard.openEndedStartCandidates("Nghe nhac tu 8h15 toi den gio"))
    }

    @Test
    fun `C2 an unaccented open-ended sentence is reconciled to now exactly like the accented one`() {
        val now = LocalTime.of(19, 10)
        val model = prop("Chạy bộ", 120, "17:00", "19:00", "health")
        val plain = kept("Chay bo tu 17h den bay gio", model, now = now).single()
        val accented = kept("Chạy bộ từ 17h đến bây giờ", model, now = now).single()
        assertEquals("19:10", plain.endLocal)
        assertEquals(accented.endLocal, plain.endLocal)
        assertEquals(accented.durationSeconds, plain.durationSeconds)
        // bắt đầu mà AI đưa không khớp giờ người viết: không được chỉnh theo "đến giờ" (giữ nguyên như câu có dấu)
        assertEquals("17:00", kept("Hoc tieng anh tu 19h toi gio", prop("Học", 120, "15:00", "17:00", "learn"), now = LocalTime.of(21, 0)).single().endLocal)
        assertEquals("21:00", kept("Hoc tieng anh tu 19h toi gio", prop("Học", 120, "19:00", "21:00", "learn"), now = LocalTime.of(21, 0)).single().endLocal)
    }

    // ================================================================================================ C3
    private fun replyOf(user: String, reply: String, now: LocalTime): String? =
        GeminiProposalClient.parseAndValidateResponseJson(
            JSONObject().put(
                "choices",
                JSONArray().put(JSONObject().put("message", JSONObject().put("content", JSONObject().put("reply", reply).put("proposals", JSONArray()).put("queries", JSONArray()).toString())))
            ).toString(),
            user, today, now
        ).reply

    private val nowDefault: LocalTime = LocalTime.of(16, 30)

    private fun assertKept(user: String, vararg replies: String, now: LocalTime = nowDefault) =
        replies.forEach { assertEquals("phải được giữ (người dùng: \"$user\", bây giờ $now): $it", it, replyOf(user, it, now)) }

    private fun assertDropped(user: String, vararg replies: String, now: LocalTime = nowDefault) =
        replies.forEach { assertNull("phải bị bỏ (người dùng: \"$user\", bây giờ $now): $it", replyOf(user, it, now)) }

    @Test
    fun `C3a a question may repeat the current time of day that the app gave the model, in every usual form`() {
        assertKept(
            "Tối nay tôi học",
            "Bây giờ là 16:30, bạn bắt đầu học lúc mấy giờ?",
            "Bây giờ mới 16h30, bạn bắt đầu học lúc nào?",
            "Hiện tại là 4h30 chiều, bạn bắt đầu học lúc nào?",
            "Mới 4h30 chiều, bạn bắt đầu học lúc nào?",
            "Bây giờ là 4 giờ 30 chiều rồi, bạn học từ lúc mấy giờ?",
            "Lúc này là 16 giờ 30, bạn muốn ghi học từ lúc mấy giờ?",
            "Bây giờ mới 4 rưỡi chiều, bạn học từ lúc mấy giờ?",
            "Bây giờ mới 4 rưỡi, bạn học từ lúc nào?",
            "Giờ là 4h30, bạn bắt đầu học lúc nào?"
        )
        assertKept("Học 2h", "Bây giờ là 17:45, bạn học từ lúc nào?", now = LocalTime.of(17, 45))
        assertKept("Học 2h", "Bây giờ là 9h05 sáng, bạn học từ lúc nào?", "Bây giờ là 9:05, bạn học từ lúc nào?", now = LocalTime.of(9, 5))
        assertKept("Học 2h", "Bây giờ là 4 giờ chiều, bạn học từ lúc nào?", "Bây giờ mới 16h, bạn bắt đầu lúc mấy giờ?", now = LocalTime.of(16, 0))
        assertKept("Học 2h", "Bây giờ là 10 giờ 30 tối, bạn học từ lúc nào?", "Bây giờ mới 22:30, bạn học từ lúc nào?", now = LocalTime.of(22, 30))
    }

    @Test
    fun `C3a only the exact current time is accepted, only inside a question, and only when the time was passed in`() {
        // sai giờ hoặc sai buổi
        assertDropped(
            "Tối nay tôi học",
            "Bây giờ là 17:30, bạn bắt đầu học lúc mấy giờ?", "Bây giờ mới 5h30 chiều, bạn học từ lúc nào?", "Bây giờ mới 5 rưỡi chiều, bạn học từ lúc nào?",
            "Bây giờ là 4h30 sáng, bạn học từ lúc nào?", "Bây giờ là 16:31, bạn học từ lúc nào?", "Bây giờ mới 5 rưỡi, bạn học từ lúc nào?"
        )
        // giờ 12h không buổi và không có "bây giờ" là thời lượng 4h30 chứ không phải giờ hiện tại; "4 tiếng rưỡi" là thời lượng
        assertDropped("Tối nay tôi học", "Bạn muốn ghi 4h30 phải không?", "Bạn học 4 tiếng rưỡi phải không?", "Bạn học 4 giờ rưỡi đúng không?")
        // câu khẳng định không có ngoại lệ này (kể cả khi đứng cạnh câu hỏi hợp lệ)
        assertDropped("Tối nay tôi học", "Bây giờ là 16:30.", "Bây giờ là 16:30. Bạn học từ lúc nào?", "Mới 4 rưỡi chiều rồi nhé.")
        // đổi "bây giờ" là đổi kết quả: cùng một câu bị bỏ nếu giờ ứng dụng đưa là giờ khác
        assertDropped("Tối nay tôi học", "Bây giờ là 16:30, bạn bắt đầu học lúc mấy giờ?", now = LocalTime.of(9, 0))
        // gọi guard KHÔNG truyền giờ hiện tại (API cũ): không có ngoại lệ
        assertNull(ReplyGuard.sanitize("Bây giờ là 16:30, bạn bắt đầu học lúc mấy giờ?", "Tối nay tôi học", emptyList(), false).first)
    }

    @Test
    fun `C3a property - for every half hour and every third minute of the day only that very minute is accepted`() {
        for (minuteOfDay in 0 until 1440 step 3) {
            val now = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            val h = now.hour; val m = now.minute
            val same = "Bây giờ là %d:%02d, bạn bắt đầu lúc mấy giờ?".format(h, m)
            val other = LocalTime.of(((minuteOfDay + 1) % 1440) / 60, (minuteOfDay + 1) % 60)
            assertEquals("giữ ở đúng giờ: $now", same, replyOf("Tối nay tôi học", same, now))
            assertNull("giờ khác ($other) phải bị bỏ: $same", replyOf("Tối nay tôi học", same, other))
            if (m == 30 && h in 1..23) {
                val h12 = if (h % 12 == 0) 12 else h % 12
                val half = "Bây giờ mới $h12 rưỡi, bạn học từ lúc nào?"
                assertEquals(half, replyOf("Tối nay tôi học", half, now))
                assertNull(half, replyOf("Tối nay tôi học", half, other))
            }
        }
    }

    @Test
    fun `C3b two figures that share one unit are both the user's figures`() {
        assertKept("Đạp xe khoảng 20 hay 25 phút", "Bạn đạp xe 20 phút hay 25 phút?", "Bạn muốn ghi 20 hay 25 phút?", "Bạn đạp xe 25 hay 20 phút?")
        assertKept("Đọc sách 1 hoặc 2 tiếng", "Bạn đọc sách 1 tiếng hay 2 tiếng?", "Bạn đọc 60 phút hay 120 phút?")
        assertKept("Tập 45 hoặc 60 phút", "Bạn tập 45 hay 60 phút?", "Bạn tập 45 phút hay 1 tiếng?")
        assertKept("Dap xe khoang 20 hay 25 phut", "Bạn đạp xe 20 phút hay 25 phút?")
        assertKept("Đọc sách ba mươi hay bốn mươi phút", "Bạn đọc 30 phút hay 40 phút?", "Bạn đọc ba mươi phút hay bốn mươi phút?")
        assertKept("Chạy 1,5 hay 2 giờ", "Bạn chạy 1,5 giờ hay 2 giờ?")
        // số nào người dùng không viết thì vẫn bị chặn
        assertDropped("Đạp xe khoảng 20 hay 25 phút", "Bạn đạp xe 30 phút hay 25 phút?", "Bạn đạp xe 20 phút hay 2 tiếng?", "Bạn đạp xe 45 phút phải không?")
        // hai số không chung một đơn vị (không có đơn vị) không phải "số của người dùng"
        assertDropped("Đạp xe 20 hay 25", "Bạn đạp xe 20 phút hay 25 phút?")
        // câu khẳng định vẫn không có ngoại lệ
        assertDropped("Đạp xe khoảng 20 hay 25 phút", "Bạn đạp xe 20 phút.", "Mình nghĩ bạn đạp 25 phút.")
    }

    @Test
    fun `C3c a span exactly derivable from two clock times the user wrote may be repeated`() {
        assertKept("Làm việc từ 9h đến 11h30", "Bạn làm việc 2 tiếng 30 phút đúng không?", "Bạn làm việc 150 phút phải không?", "Bạn làm việc 2h30 đúng không?")
        assertKept("Làm việc từ 8h đến 9h", "Bạn làm việc 1 tiếng đúng không?", "Bạn làm việc 60 phút phải không?")
        assertKept("Học 3 tiếng, từ 13h đến 15h", "Bạn học 3 tiếng hay 2 tiếng (từ 13h đến 15h)?")
        assertKept("Học 3 tiếng, từ 13h đến 15h", "Bạn học 3 tiếng hay 2 tiếng?")
        // buổi được tính: "2h-4h chiều" là 14:00-16:00
        assertKept("Dạy kèm từ 2h đến 4h chiều", "Bạn dạy 2 tiếng đúng không?", "Bạn dạy từ 14h đến 16h, đúng không?", "Bạn dạy từ 2h đến 4h chiều đúng không?")
        // một giờ có buổi
        assertKept("Xem phim lúc 7h tối", "Bạn bắt đầu lúc 19h đúng không?", "Bạn bắt đầu lúc 19:00 phải không?")
        // gõ không dấu
        assertKept("Lam viec tu 9h den 11h30", "Bạn làm việc 2 tiếng 30 phút đúng không?")
        // cái gì không suy ra được thì vẫn bị chặn
        assertDropped("Làm việc từ 9h đến 11h30", "Bạn làm việc 3 tiếng phải không?", "Bạn làm việc đến 12h phải không?", "Bạn làm việc 2 tiếng đúng không?")
        assertDropped("Học từ 2h đến 4h", "Bạn học từ 14h phải không?")                       // không đoán sáng/chiều
        assertDropped("Xem phim lúc 7h tối", "Bạn bắt đầu lúc 20h đúng không?")
        // không có hai mốc giờ thì không có khoảng
        assertDropped("Làm việc từ 9h", "Bạn làm việc 2 tiếng phải không?", "Bạn làm việc đến 11h phải không?")
        // câu khẳng định không có ngoại lệ
        assertDropped("Làm việc từ 9h đến 11h30", "Bạn làm việc 2 tiếng 30 phút.", "Hôm nay bạn làm 150 phút rồi.")
    }

    @Test
    fun `C3 everything else stays blocked - statements, invented figures, percentages, counts, ledger claims and one bad sentence drops the reply`() {
        val u = "Làm việc từ 9h đến 11h30"
        assertDropped(
            u,
            "Bây giờ là 16:30, mình đã lưu rồi đúng không?",             // khẳng định đã ghi
            "Bây giờ là 16:30, bạn làm việc 50% đúng không?",             // phần trăm
            "Bây giờ là 16:30, bạn làm 3 lần phải không?",                // số lần
            "Bây giờ là 16:30, bạn làm việc 7 tiếng đúng không?",         // số bịa
            "Bây giờ là 16:30. Hôm nay bạn đã dùng 5 giờ. Bạn bắt đầu lúc nào?",
            "Mình đã ghi xong rồi nhé?",
            "Do you mean two hours?"
        )
        assertDropped("Tối nay tôi học", "Bây giờ mới 16:30, bạn học 20 phút hay 30 phút?", "Bạn học 2 hay 3 tiếng?")
    }

    @Test
    fun `C3 a clarifying question that survives is the only text of the outcome, and an invented figure still drops it with a note`() {
        val ask = "Bây giờ mới 4 rưỡi chiều, bạn xem phim từ lúc mấy giờ?"
        assertEquals(ask, replyOf("Xem phim tối nay", ask, LocalTime.of(16, 30)))
        val body = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", """{"reply":"Bây giờ mới 4 rưỡi chiều, bạn xem 3 tiếng rồi đúng không?","proposals":[],"queries":[]}""")))
        ).toString()
        val bad = GeminiProposalClient.parseAndValidateResponseJson(body, "Xem phim tối nay", today, LocalTime.of(16, 30))
        assertNull(bad.reply)
        assertTrue(bad.warnings.joinToString(), bad.warnings.any { it.contains("số liệu không có căn cứ") })
    }
}
