/**
 * Bài kiểm thử cấu trúc & ngữ nghĩa (Schema + Semantic Validation Test)
 * cho AI Proposal Extractor của ứng dụng Time Wallet (TypeScript / Node.js).
 *
 * Hỗ trợ chạy trực tiếp bằng Node 22+ (TypeScript native):
 *   node --experimental-strip-types ai_test/test_foza_ai.ts
 * Hoặc chạy bản ESM:
 *   node ai_test/test_foza_ai.mjs
 */

export type TimeCategoryKey =
  | 'work'
  | 'learn'
  | 'health'
  | 'life'
  | 'rest'
  | 'social'
  | 'fun'
  | 'sleep'
  | 'other';

export type TimeEvidenceKind =
  | 'explicit_range'
  | 'anchored_from_now'
  | 'duration_only';

export interface ActivityProposalSchema {
  title: string;
  category: TimeCategoryKey;
  durationSeconds: number;
  dateLocal: string;
  startLocal: string | null;
  endLocal: string | null;
  timeEvidence: TimeEvidenceKind;
  missingFields: Array<'startLocal' | 'endLocal'>;
  needsConfirmation: true;
}

export interface ProposalEnvelopeSchema {
  proposals: ActivityProposalSchema[];
}

export interface RealTimeContext {
  currentDate: string; // YYYY-MM-DD
  currentTime: string; // HH:mm
  dayOfWeek: string;
  timezone: string;
}

const API_KEY = process.env.FOZA_API_KEY || 'sk-YT4B-jOV9gKtnvCftAlXyhQii6KsNPY2hFVbGA8uFrqJi9tb';
const BASE_URL = process.env.FOZA_BASE_URL || 'https://api.foza.ai/v1';
const MODEL = process.env.FOZA_MODEL || 'cinb/gemini-3.8-flash';

const ALLOWED_CATEGORIES = new Set<string>([
  'work', 'learn', 'health', 'life', 'rest', 'social', 'fun', 'sleep', 'other'
]);

const ALLOWED_TIME_EVIDENCE = new Set<string>([
  'explicit_range',
  'anchored_from_now',
  'duration_only'
]);

export function buildSystemPrompt(ctx: RealTimeContext): string {
  return `Bạn là AI phân tích nhật ký thời gian cho ứng dụng Time Wallet.
NGỮ CẢNH THỜI GIAN THỰC CỦA NGƯỜI DÙNG:
- Ngày hiện tại (dateLocal): ${ctx.currentDate} (${ctx.dayOfWeek})
- Giờ hiện tại (currentTime): ${ctx.currentTime}
- Múi giờ (timezone): ${ctx.timezone}

NHIỆM VỤ:
Phân tích câu tiếng Việt của người dùng thành mảng JSON "proposals" (tối đa 8 hoạt động).

QUY TẮC SUY LUẬN THỜI GIAN BẮT BUỘC:
1. NEO THEO GIỜ THỰC TẾ HIỆN TẠI (${ctx.currentTime}):
   - Nếu người dùng nói hành động bắt đầu ngay bây giờ / sắp thực hiện liền (ví dụ: ở lúc 02:00 sáng nhắn "tôi sẽ ngủ 5h tiếng", "bây giờ đi ngủ 5 tiếng", "giờ mình sẽ nghỉ uống cà phê 20 phút"):
     + Đặt startLocal = "${ctx.currentTime}" (ví dụ "02:00")
     + Tính endLocal = startLocal + thời lượng (ví dụ 02:00 + 5 tiếng = "07:00")
     + durationSeconds = thời lượng tính bằng giây (5 tiếng = 18000)
     + timeEvidence = "anchored_from_now", missingFields = []
   - Nếu người dùng nói vừa mới kết thúc tức thì ("vừa mới học xong 45 phút", "mới chạy bộ xong 30p"):
     + Đặt endLocal = "${ctx.currentTime}"
     + Tính startLocal = endLocal - thời lượng
     + timeEvidence = "anchored_from_now", missingFields = []

2. KHOẢNG GIỜ TƯỜNG MINH:
   - Nếu câu nêu rõ từ giờ nào đến giờ nào (ví dụ "Từ 14h đến 15h30 làm dự án AI"):
     + Điền startLocal ("HH:mm"), endLocal ("HH:mm"), tính đúng durationSeconds.
     + timeEvidence = "explicit_range", missingFields = []

3. CHỈ CÓ THỜI LƯỢNG (KHÔNG NEO GIỜ HIỆN TẠI, KHÔNG NÊU KHOẢNG GIỜ):
   - Nếu người dùng chỉ kể tổng kết chung trong ngày mà không có mốc giờ và không có ý "bắt đầu ngay lúc này" hay "vừa mới xong" (ví dụ: "Hôm nay học Feynman 45 phút, xem YouTube 30 phút"):
     + BẮT BUỘC để startLocal = null, endLocal = null
     + timeEvidence = "duration_only", missingFields = ["startLocal", "endLocal"]

4. DANH MỤC HỢP LỆ ("category"):
   Chỉ được chọn đúng 1 trong 9 giá trị: "work", "learn", "health", "life", "rest", "social", "fun", "sleep", "other".

5. BẤT BIẾN AN TOÀN:
   AI không được tự ghi sổ cái. Mọi phần tử trong "proposals" BẮT BUỘC LUÔN LUÔN có "needsConfirmation": true (kể cả khi đã có đủ startLocal và endLocal, tuyệt đối không đặt false).

6. ĐỊNH DẠNG ĐẦU RA:
   CHỈ trả về đúng một JSON object hợp lệ theo cấu trúc:
{
  "proposals": [
    {
      "title": "Ngủ",
      "category": "sleep",
      "durationSeconds": 18000,
      "dateLocal": "${ctx.currentDate}",
      "startLocal": "02:00",
      "endLocal": "07:00",
      "timeEvidence": "anchored_from_now",
      "missingFields": [],
      "needsConfirmation": true
    }
  ]
}`;
}

export function validateProposalResponseSchema(payload: unknown): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    return { valid: false, errors: ['Root payload phải là một JSON object.'] };
  }

  const rootObj = payload as Record<string, unknown>;
  const allowedRootKeys = new Set(['proposals']);
  for (const key of Object.keys(rootObj)) {
    if (!allowedRootKeys.has(key)) {
      errors.push(`Trường lạ ở root không được phép (extra="forbid"): "${key}"`);
    }
  }

  if (!Array.isArray(rootObj.proposals) || rootObj.proposals.length === 0) {
    errors.push('"proposals" phải là mảng không rỗng.');
    return { valid: false, errors };
  }

  const allowedItemKeys = new Set([
    'title',
    'category',
    'durationSeconds',
    'dateLocal',
    'startLocal',
    'endLocal',
    'timeEvidence',
    'missingFields',
    'needsConfirmation'
  ]);

  rootObj.proposals.forEach((rawItem: unknown, idx: number) => {
    const prefix = `proposals[${idx}]`;
    if (!rawItem || typeof rawItem !== 'object' || Array.isArray(rawItem)) {
      errors.push(`${prefix} phải là object.`);
      return;
    }
    const item = rawItem as Record<string, unknown>;

    for (const k of Object.keys(item)) {
      if (!allowedItemKeys.has(k)) {
        errors.push(`${prefix}: trường lạ không được phép "${k}"`);
      }
    }

    if (typeof item.title !== 'string' || item.title.trim().length === 0) {
      errors.push(`${prefix}.title phải là chuỗi không rỗng.`);
    }
    if (typeof item.category !== 'string' || !ALLOWED_CATEGORIES.has(item.category)) {
      errors.push(`${prefix}.category="${String(item.category)}" không nằm trong danh sách cho phép.`);
    }
    if (
      typeof item.durationSeconds !== 'number' ||
      !Number.isInteger(item.durationSeconds) ||
      item.durationSeconds <= 0 ||
      item.durationSeconds > 86400
    ) {
      errors.push(`${prefix}.durationSeconds="${String(item.durationSeconds)}" phải là số nguyên dương (1..86400).`);
    }
    if (typeof item.dateLocal !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(item.dateLocal)) {
      errors.push(`${prefix}.dateLocal="${String(item.dateLocal)}" phải đúng định dạng YYYY-MM-DD.`);
    }

    const hasStart = item.startLocal !== null && item.startLocal !== undefined;
    const hasEnd = item.endLocal !== null && item.endLocal !== undefined;
    if (hasStart !== hasEnd) {
      errors.push(`${prefix}: startLocal và endLocal phải cùng có hoặc cùng là null.`);
    }

    if (hasStart && hasEnd) {
      if (typeof item.startLocal !== 'string' || !/^\d{2}:\d{2}$/.test(item.startLocal)) {
        errors.push(`${prefix}.startLocal="${String(item.startLocal)}" phải có định dạng HH:mm.`);
      }
      if (typeof item.endLocal !== 'string' || !/^\d{2}:\d{2}$/.test(item.endLocal)) {
        errors.push(`${prefix}.endLocal="${String(item.endLocal)}" phải có định dạng HH:mm.`);
      }
      if (
        typeof item.startLocal === 'string' &&
        typeof item.endLocal === 'string' &&
        /^\d{2}:\d{2}$/.test(item.startLocal) &&
        /^\d{2}:\d{2}$/.test(item.endLocal) &&
        typeof item.durationSeconds === 'number'
      ) {
        const [sh, sm] = item.startLocal.split(':').map(Number);
        const [eh, em] = item.endLocal.split(':').map(Number);
        let diffMinutes = (eh * 60 + em) - (sh * 60 + sm);
        if (diffMinutes <= 0) diffMinutes += 1440;
        const expectedSeconds = diffMinutes * 60;
        if (Math.abs(expectedSeconds - item.durationSeconds) > 120) {
          errors.push(
            `${prefix}: Khoảng giờ ${item.startLocal}->${item.endLocal} (${expectedSeconds}s) lệch với durationSeconds (${item.durationSeconds}s).`
          );
        }
      }
    }

    if (typeof item.timeEvidence !== 'string' || !ALLOWED_TIME_EVIDENCE.has(item.timeEvidence)) {
      errors.push(`${prefix}.timeEvidence="${String(item.timeEvidence)}" không hợp lệ.`);
    }
    if (!Array.isArray(item.missingFields)) {
      errors.push(`${prefix}.missingFields phải là mảng.`);
    }
    if (item.needsConfirmation !== true) {
      errors.push(`${prefix}.needsConfirmation bắt buộc phải là true.`);
    }
  });

  return { valid: errors.length === 0, errors };
}

async function callFozaChatCompletion(context: RealTimeContext, userMessage: string, retries = 2): Promise<ProposalEnvelopeSchema> {
  const systemPrompt = buildSystemPrompt(context);
  for (let attempt = 0; attempt <= retries; attempt++) {
    const response = await fetch(`${BASE_URL}/chat/completions`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: MODEL,
        temperature: 0.1,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: userMessage }
        ]
      })
    });

    if (response.status === 429 && attempt < retries) {
      await new Promise((r) => setTimeout(r, 1200 * (attempt + 1)));
      continue;
    }

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`HTTP ${response.status}: ${errText}`);
    }

    const data = (await response.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const rawContent = data?.choices?.[0]?.message?.content || '';
    const cleaned = rawContent
      .replace(/^```json\s*/i, '')
      .replace(/^```\s*/i, '')
      .replace(/\s*```$/i, '')
      .trim();

    return JSON.parse(cleaned) as ProposalEnvelopeSchema;
  }
  throw new Error('Exceeded max retries');
}

interface TestCaseSpec {
  id: string;
  description: string;
  context: RealTimeContext;
  userInput: string;
  assertSemantic: (proposals: ActivityProposalSchema[]) => string[];
}

const TEST_CASES: TestCaseSpec[] = [
  {
    id: 'TC01_SLEEP_2AM_5HOURS',
    description: 'Ngữ cảnh 2h sáng: "tôi sẽ ngủ 5h tiếng" -> phải hiểu ngủ từ 02:00 đến 07:00 (18000s)',
    context: {
      currentDate: '2026-11-12',
      currentTime: '02:00',
      dayOfWeek: 'Thứ Năm',
      timezone: 'Asia/Ho_Chi_Minh'
    },
    userInput: 'tôi sẽ ngủ 5h tiếng',
    assertSemantic: (proposals) => {
      const p = proposals[0];
      const errs: string[] = [];
      if (proposals.length !== 1) errs.push(`Kỳ vọng 1 proposal, nhận ${proposals.length}`);
      if (p.category !== 'sleep') errs.push(`Kỳ vọng category="sleep", nhận "${p.category}"`);
      if (p.startLocal !== '02:00') errs.push(`Kỳ vọng startLocal="02:00", nhận "${String(p.startLocal)}"`);
      if (p.endLocal !== '07:00') errs.push(`Kỳ vọng endLocal="07:00", nhận "${String(p.endLocal)}"`);
      if (p.durationSeconds !== 18000) errs.push(`Kỳ vọng durationSeconds=18000, nhận ${p.durationSeconds}`);
      if (p.dateLocal !== '2026-11-12') errs.push(`Kỳ vọng dateLocal="2026-11-12", nhận "${p.dateLocal}"`);
      return errs;
    }
  },
  {
    id: 'TC02_JUST_FINISHED_AT_10_45',
    description: 'Ngữ cảnh 10:45 sáng: "vừa mới học kỹ thuật Feynman xong 45 phút" -> hiểu từ 10:00 đến 10:45',
    context: {
      currentDate: '2026-11-12',
      currentTime: '10:45',
      dayOfWeek: 'Thứ Năm',
      timezone: 'Asia/Ho_Chi_Minh'
    },
    userInput: 'vừa mới học kỹ thuật Feynman xong 45 phút',
    assertSemantic: (proposals) => {
      const p = proposals[0];
      const errs: string[] = [];
      if (proposals.length !== 1) errs.push(`Kỳ vọng 1 proposal, nhận ${proposals.length}`);
      if (p.category !== 'learn') errs.push(`Kỳ vọng category="learn", nhận "${p.category}"`);
      if (p.startLocal !== '10:00') errs.push(`Kỳ vọng startLocal="10:00", nhận "${String(p.startLocal)}"`);
      if (p.endLocal !== '10:45') errs.push(`Kỳ vọng endLocal="10:45", nhận "${String(p.endLocal)}"`);
      if (p.durationSeconds !== 2700) errs.push(`Kỳ vọng durationSeconds=2700, nhận ${p.durationSeconds}`);
      return errs;
    }
  },
  {
    id: 'TC03_DURATION_ONLY_UNPLACED',
    description: 'Tổng kết trong ngày không neo giờ: "Hôm nay học Feynman 45 phút, xem YouTube 30 phút" -> 2 bản ghi unplaced (startLocal=null)',
    context: {
      currentDate: '2026-11-12',
      currentTime: '21:15',
      dayOfWeek: 'Thứ Năm',
      timezone: 'Asia/Ho_Chi_Minh'
    },
    userInput: 'Hôm nay học Feynman 45 phút, xem YouTube 30 phút',
    assertSemantic: (proposals) => {
      const errs: string[] = [];
      if (proposals.length !== 2) {
        errs.push(`Kỳ vọng 2 proposals, nhận ${proposals.length}`);
        return errs;
      }
      const [p1, p2] = proposals;
      if (p1.category !== 'learn' || p1.durationSeconds !== 2700) {
        errs.push(`Proposal 1 kỳ vọng learn/2700s, nhận ${p1.category}/${p1.durationSeconds}s`);
      }
      if (p1.startLocal !== null || p1.endLocal !== null) {
        errs.push(`Proposal 1 không có mốc giờ nên startLocal/endLocal phải là null, nhận ${String(p1.startLocal)}-${String(p1.endLocal)}`);
      }
      if (p2.category !== 'fun' || p2.durationSeconds !== 1800) {
        errs.push(`Proposal 2 kỳ vọng fun/1800s, nhận ${p2.category}/${p2.durationSeconds}s`);
      }
      if (p2.startLocal !== null || p2.endLocal !== null) {
        errs.push(`Proposal 2 không có mốc giờ nên startLocal/endLocal phải là null, nhận ${String(p2.startLocal)}-${String(p2.endLocal)}`);
      }
      return errs;
    }
  },
  {
    id: 'TC04_EXPLICIT_TIME_RANGE',
    description: 'Khoảng giờ cụ thể: "Từ 14h đến 15h30 tôi làm dự án AI" -> 14:00 đến 15:30 (5400s)',
    context: {
      currentDate: '2026-11-12',
      currentTime: '18:00',
      dayOfWeek: 'Thứ Năm',
      timezone: 'Asia/Ho_Chi_Minh'
    },
    userInput: 'Từ 14h đến 15h30 tôi làm dự án AI',
    assertSemantic: (proposals) => {
      const p = proposals[0];
      const errs: string[] = [];
      if (proposals.length !== 1) errs.push(`Kỳ vọng 1 proposal, nhận ${proposals.length}`);
      if (p.category !== 'work') errs.push(`Kỳ vọng category="work", nhận "${p.category}"`);
      if (p.startLocal !== '14:00' || p.endLocal !== '15:30') {
        errs.push(`Kỳ vọng 14:00->15:30, nhận ${String(p.startLocal)}->${String(p.endLocal)}`);
      }
      if (p.durationSeconds !== 5400) errs.push(`Kỳ vọng durationSeconds=5400, nhận ${p.durationSeconds}`);
      return errs;
    }
  },
  {
    id: 'TC05_MIXED_EXPLICIT_AND_NOW',
    description: 'Kết hợp khoảng giờ + hành động ngay lúc 09:40: "Từ 8h đến 9h chạy bộ, bây giờ mình sẽ nghỉ uống cà phê 20 phút"',
    context: {
      currentDate: '2026-11-12',
      currentTime: '09:40',
      dayOfWeek: 'Thứ Năm',
      timezone: 'Asia/Ho_Chi_Minh'
    },
    userInput: 'Từ 8h đến 9h chạy bộ, bây giờ mình sẽ nghỉ uống cà phê 20 phút',
    assertSemantic: (proposals) => {
      const errs: string[] = [];
      if (proposals.length !== 2) {
        errs.push(`Kỳ vọng 2 proposals, nhận ${proposals.length}`);
        return errs;
      }
      const [p1, p2] = proposals;
      if (p1.category !== 'health' || p1.startLocal !== '08:00' || p1.endLocal !== '09:00') {
        errs.push(`Proposal 1 kỳ vọng health 08:00->09:00, nhận ${p1.category} ${String(p1.startLocal)}->${String(p1.endLocal)}`);
      }
      if (p2.category !== 'rest' || p2.startLocal !== '09:40' || p2.endLocal !== '10:00') {
        errs.push(`Proposal 2 kỳ vọng rest 09:40->10:00, nhận ${p2.category} ${String(p2.startLocal)}->${String(p2.endLocal)}`);
      }
      return errs;
    }
  }
];

async function runAllTests(): Promise<void> {
  console.log('================================================================');
  console.log(`Testing AI Proposals via ${BASE_URL} | Model: ${MODEL}`);
  console.log('================================================================\n');

  // Stagger requests by 650ms to respect Foza API rate limit (Max 2 req/s)
  const results = await Promise.all(
    TEST_CASES.map(async (tc, index) => {
      await new Promise((r) => setTimeout(r, index * 650));
      try {
        const parsed = await callFozaChatCompletion(tc.context, tc.userInput);
        const schemaResult = validateProposalResponseSchema(parsed);
        const semanticErrors = schemaResult.valid ? tc.assertSemantic(parsed.proposals) : [];
        const allErrors = [...schemaResult.errors, ...semanticErrors];
        return { tc, parsed, allErrors, fatalError: null as string | null };
      } catch (err) {
        return {
          tc,
          parsed: null,
          allErrors: [] as string[],
          fatalError: err instanceof Error ? err.message : String(err)
        };
      }
    })
  );

  let passed = 0;
  let failed = 0;

  for (const res of results) {
    const { tc, parsed, allErrors, fatalError } = res;
    console.log(`▶ [${tc.id}] ${tc.description}`);
    console.log(`  Context : ${tc.context.currentDate} ${tc.context.currentTime} (${tc.context.timezone})`);
    console.log(`  Input   : "${tc.userInput}"`);

    if (fatalError) {
      console.log(`  Result  : ❌ ERROR: ${fatalError}\n`);
      failed++;
      continue;
    }

    console.log(`  Output  : ${JSON.stringify(parsed)}`);
    if (allErrors.length === 0) {
      console.log(`  Result  : ✅ PASS (Cấu trúc JSON & Ngữ nghĩa thời gian chuẩn)\n`);
      passed++;
    } else {
      console.log(`  Result  : ❌ FAIL`);
      allErrors.forEach((e) => console.log(`    - ${e}`));
      console.log('');
      failed++;
    }
  }

  console.log('================================================================');
  console.log(`TỔNG KẾT: ${passed}/${TEST_CASES.length} PASS, ${failed} FAIL`);
  console.log('================================================================');

  if (failed > 0) {
    process.exitCode = 1;
  }
}

runAllTests();
