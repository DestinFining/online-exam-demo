package com.example.exam.question;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class QuestionExcelService {
    private static final int MAX_IMPORT_ROWS = 2000;
    private static final int MAX_EXPORT_ROWS = 50000;
    private static final String[] HEADERS = {"学科ID", "知识点ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "正确选项", "答案配置", "解析", "难度", "难度系数", "分值", "标签"};
    private static final Set<String> CHOICE_TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionService questionService;

    public QuestionExcelService(QuestionMapper questionMapper, QuestionOptionMapper optionMapper, QuestionService questionService) {
        this.questionMapper = questionMapper; this.optionMapper = optionMapper; this.questionService = questionService;
    }

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题目导入模板");
            createHeader(workbook, sheet);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            int[] widths = {3000, 3200, 5200, 10000, 6000, 6000, 6000, 6000, 6000, 6000, 3800, 8500, 8500, 3200, 3600, 2600, 4200};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);
            Sheet help = workbook.createSheet("填写说明");
            String[][] lines = {
                    {"字段", "填写规则"},
                    {"学科ID、知识点ID", "必填数字 ID，可在系统学科/知识点管理页面查看。知识点必须属于对应学科。"},
                    {"题型", "SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER / PROGRAMMING；也支持单选题/多选题/判断题/填空题/简答题/编程题。"},
                    {"题干", "必填。"},
                    {"选项A-F", "选择题填写选项内容；最多支持 6 个选项。非选择题可留空。"},
                    {"正确选项", "选择题填写选项标识，例如 A 或 A,C；多选题多个答案用英文逗号分隔。"},
                    {"答案配置", "填空题建议填写标准答案文本，多个答案用 | 分隔；简答题/编程题填写参考答案。可以直接填写文本，也可以填写 JSON。"},
                    {"解析", "可选。"},
                    {"难度", "EASY / MEDIUM / HARD，也支持简单/中等/困难；默认中等。"},
                    {"难度系数", "0 到 1 的小数；如果留空则按难度等级设置默认值。"},
                    {"分值", "必填正数。"},
                    {"标签", "可选文本。"},
                    {"导入行为", "每行独立导入，失败行不会影响其他有效行；结果会返回失败的 Excel 行号和原因。最多 2000 条数据行。"}
            };
            for (int r = 0; r < lines.length; r++) {
                Row row = help.createRow(r);
                for (int c = 0; c < lines[r].length; c++) row.createCell(c).setCellValue(lines[r][c]);
            }
            CellStyle helpHeader = workbook.createCellStyle(); helpHeader.cloneStyleFrom(headerStyle(workbook));
            for (Cell cell : help.getRow(0)) cell.setCellStyle(helpHeader);
            help.setColumnWidth(0, 6000); help.setColumnWidth(1, 25000); help.createFreezePane(0, 1);
            workbook.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessException(500, "生成 Excel 模板失败"); }
    }

    public byte[] exportExcel(Long subjectId, Long knowledgePointId, String type, String difficulty, String keyword) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题目数据"); createHeader(workbook, sheet);
            QueryWrapper<Question> query = new QueryWrapper<Question>().eq("status", 1)
                    .eq(subjectId != null, "subject_id", subjectId)
                    .eq(knowledgePointId != null, "knowledge_point_id", knowledgePointId)
                    .eq(type != null && !type.isBlank(), "type", normalizeType(type))
                    .eq(difficulty != null && !difficulty.isBlank(), "difficulty", normalizeDifficulty(difficulty))
                    .like(keyword != null && !keyword.isBlank(), "content", keyword == null ? null : keyword.trim())
                    .orderByDesc("id").last("LIMIT " + MAX_EXPORT_ROWS);
            List<Question> questions = questionMapper.selectList(query);
            for (int idx = 0; idx < questions.size(); idx++) {
                Question question = questions.get(idx);
                List<QuestionOption> options = optionMapper.selectList(new QueryWrapper<QuestionOption>().eq("question_id", question.getId()).orderByAsc("sort").orderByAsc("id"));
                Row row = sheet.createRow(idx + 1);
                set(row, 0, question.getSubjectId()); set(row, 1, question.getKnowledgePointId()); set(row, 2, question.getType()); set(row, 3, question.getContent());
                StringJoiner correct = new StringJoiner(",");
                for (int optionIndex = 0; optionIndex < options.size() && optionIndex < 6; optionIndex++) {
                    QuestionOption option = options.get(optionIndex);
                    String exportedKey = String.valueOf((char) ('A' + optionIndex));
                    set(row, 4 + optionIndex, option.getOptionContent());
                    if (Integer.valueOf(1).equals(option.getIsCorrect())) correct.add(exportedKey);
                }
                set(row, 10, correct.toString()); set(row, 11, question.getAnswerConfig()); set(row, 12, question.getAnalysis());
                set(row, 13, question.getDifficulty()); set(row, 14, question.getDifficultyValue()); set(row, 15, question.getDefaultScore()); set(row, 16, question.getTags());
            }
            int[] widths = {3000, 3200, 5200, 10000, 6000, 6000, 6000, 6000, 6000, 6000, 3800, 8500, 8500, 3200, 3600, 2600, 4200};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);
            sheet.createFreezePane(0, 1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, questions.size()), 0, HEADERS.length - 1));
            workbook.write(output); return output.toByteArray();
        } catch (IOException ex) { throw new BusinessException(500, "导出题目 Excel 失败"); }
    }

    public QuestionExcelImportResult importExcel(MultipartFile file, String username) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择待导入的 .xlsx 文件");
        if (file.getSize() > 10L * 1024 * 1024) throw new BusinessException(400, "Excel 文件不能超过 10 MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) throw new BusinessException(400, "仅支持 .xlsx 格式，请先将文件另存为 Excel 工作簿（.xlsx）");
        List<String> errors = new ArrayList<>(); int imported = 0; int totalRows = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException(400, "Excel 中没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0 || sheet.getRow(0) == null) throw new BusinessException(400, "第一行必须是模板表头");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            String h0 = text(sheet.getRow(0), 0, formatter); String h3 = text(sheet.getRow(0), 3, formatter);
            if (!"学科ID".equals(h0) || !"题干".equals(h3)) throw new BusinessException(400, "模板表头不匹配，请先下载系统提供的 Excel 导入模板");
            int last = sheet.getLastRowNum();
            if (last > MAX_IMPORT_ROWS) throw new BusinessException(400, "单次最多导入 2000 条题目，请拆分文件后重试");
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r); if (isBlank(row, formatter)) continue; totalRows++;
                try {
                    QuestionRequest request = parseRow(row, formatter, r + 1);
                    questionService.create(request, username); imported++;
                } catch (Exception ex) {
                    String message = ex instanceof BusinessException ? ex.getMessage() : "数据保存失败，请检查学科 ID、知识点 ID 和字段格式";
                    errors.add("第 " + (r + 1) + " 行：" + message);
                }
            }
        } catch (BusinessException ex) { throw ex; }
          catch (Exception ex) { throw new BusinessException(400, "读取 Excel 失败，请确认文件未损坏且使用 .xlsx 格式"); }
        return new QuestionExcelImportResult(totalRows, imported, totalRows - imported, errors);
    }

    private QuestionRequest parseRow(Row row, DataFormatter f, int excelRow) {
        Long subjectId = longValue(text(row, 0, f), "学科ID", excelRow);
        Long knowledgeId = longValue(text(row, 1, f), "知识点ID", excelRow);
        String type = normalizeType(text(row, 2, f));
        String content = text(row, 3, f).trim();
        if (content.isBlank()) throw new BusinessException(400, "题干不能为空");
        String difficulty = normalizeDifficulty(text(row, 13, f));
        if (difficulty.isBlank()) difficulty = "MEDIUM";
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw new BusinessException(400, "难度必须是 EASY/MEDIUM/HARD 或简单/中等/困难");
        BigDecimal difficultyValue = decimal(text(row, 14, f), new BigDecimal(switch (difficulty) { case "EASY" -> "0.30"; case "HARD" -> "0.80"; default -> "0.50"; }), "难度系数", excelRow);
        if (difficultyValue.compareTo(BigDecimal.ZERO) < 0 || difficultyValue.compareTo(BigDecimal.ONE) > 0) throw new BusinessException(400, "难度系数必须在 0 到 1 之间");
        BigDecimal score = decimal(text(row, 15, f), null, "分值", excelRow);
        if (score == null || score.signum() <= 0) throw new BusinessException(400, "分值必须是大于 0 的数字");
        String answerConfig = text(row, 11, f).trim();
        String correctRaw = text(row, 10, f).trim().toUpperCase(Locale.ROOT).replace('，', ',').replace('、', ',').replaceAll("\\s+", "");
        List<QuestionRequest.OptionRequest> options = new ArrayList<>();
        if (CHOICE_TYPES.contains(type)) {
            Set<String> correctKeys = new LinkedHashSet<>(); if (!correctRaw.isBlank()) for (String key : correctRaw.split(",")) if (!key.isBlank()) correctKeys.add(key);
            for (int i = 0; i < 6; i++) {
                String value = text(row, 4 + i, f).trim(); if (value.isBlank()) continue;
                String key = String.valueOf((char)('A' + i));
                options.add(new QuestionRequest.OptionRequest(key, value, options.size() + 1, correctKeys.contains(key)));
            }
            if (options.size() < 2) throw new BusinessException(400, "选择题至少需要两个选项");
            if (options.size() > 6) throw new BusinessException(400, "每道选择题最多支持六个选项 A-F");
            if ("TRUE_FALSE".equals(type) && options.size() != 2) throw new BusinessException(400, "判断题必须填写两个选项");
            if (correctKeys.isEmpty()) throw new BusinessException(400, "选择题必须填写正确选项列，例如 A 或 A,C");
            for (String key : correctKeys) if (key.length() != 1 || key.charAt(0) < 'A' || key.charAt(0) > 'F' || options.stream().noneMatch(o -> o.optionKey().equals(key))) throw new BusinessException(400, "正确选项包含不存在的选项标识：" + key);
            answerConfig = String.join(",", correctKeys);
        } else {
            if (answerConfig.isBlank()) throw new BusinessException(400, "填空/简答/编程题必须填写答案配置列");
            if ("FILL_BLANK".equals(type) && !answerConfig.trim().startsWith("{")) {
                List<String> answers = Arrays.stream(answerConfig.split("\\|", -1)).map(String::trim).filter(v -> !v.isBlank()).toList();
                if (answers.isEmpty()) throw new BusinessException(400, "填空题答案不能为空");
                answerConfig = "{\"answers\":" + toJsonArray(answers) + "}";
            } else if (Set.of("SHORT_ANSWER", "PROGRAMMING").contains(type) && !answerConfig.trim().startsWith("{")) {
                answerConfig = "{\"referenceAnswer\":" + quoteJson(answerConfig) + "}";
            }
        }
        return new QuestionRequest(subjectId, knowledgeId, type, content, answerConfig, emptyToNull(text(row, 12, f)), difficulty, difficultyValue, score, emptyToNull(text(row, 16, f)), options);
    }

    private void createHeader(Workbook workbook, Sheet sheet) {
        Row row = sheet.createRow(0); CellStyle style = headerStyle(workbook);
        for (int i = 0; i < HEADERS.length; i++) { Cell cell = row.createCell(i); cell.setCellValue(HEADERS[i]); cell.setCellStyle(style); }
    }
    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); style.setFont(font); style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); style.setWrapText(true); return style;
    }
    private void set(Row row, int column, Object value) { if (value == null) return; Cell cell = row.createCell(column); if (value instanceof Number n) cell.setCellValue(n.doubleValue()); else cell.setCellValue(String.valueOf(value)); }
    private String text(Row row, int col, DataFormatter f) { if (row == null || row.getCell(col) == null) return ""; return f.formatCellValue(row.getCell(col)).trim(); }
    private boolean isBlank(Row row, DataFormatter f) { if (row == null) return true; for (int i=0;i<HEADERS.length;i++) if (!text(row, i, f).isBlank()) return false; return true; }
    private Long longValue(String raw, String name, int row) { try { if (raw.isBlank()) throw new NumberFormatException(); return new BigDecimal(raw).longValueExact(); } catch (Exception ex) { throw new BusinessException(400, name + "必须填写整数 ID"); } }
    private BigDecimal decimal(String raw, BigDecimal fallback, String name, int row) { if (raw.isBlank()) return fallback; try { return new BigDecimal(raw); } catch (Exception ex) { throw new BusinessException(400, name + "必须是数字"); } }
    private String normalizeType(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (v) { case "单选题" -> "SINGLE_CHOICE"; case "多选题" -> "MULTIPLE_CHOICE"; case "判断题" -> "TRUE_FALSE"; case "填空题" -> "FILL_BLANK"; case "简答题" -> "SHORT_ANSWER"; case "编程题" -> "PROGRAMMING"; default -> v; };
    }
    private String normalizeDifficulty(String value) { String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); return switch (v) { case "简单" -> "EASY"; case "中等" -> "MEDIUM"; case "困难" -> "HARD"; default -> v; }; }
    private int letterIndex(String key) { if (key == null || key.length() != 1) return -1; char c = Character.toUpperCase(key.charAt(0)); return c >= 'A' && c <= 'F' ? c - 'A' : -1; }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String quoteJson(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.append('\"').toString();
    }
    private String toJsonArray(List<String> values) { return "[" + String.join(",", values.stream().map(this::quoteJson).toList()) + "]"; }
}
