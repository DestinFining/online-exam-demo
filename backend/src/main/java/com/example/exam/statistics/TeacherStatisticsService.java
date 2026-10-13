package com.example.exam.statistics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.exam.Exam;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.exam.ExamMapper;
import com.example.exam.paper.Paper;
import com.example.exam.paper.PaperMapper;
import com.example.exam.paper.PaperQuestion;
import com.example.exam.paper.PaperQuestionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeacherStatisticsService {
    private final SysUserMapper userMapper;
    private final ExamMapper examMapper;
    private final ExamAttemptMapper attemptMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;

    public TeacherStatisticsService(SysUserMapper userMapper, ExamMapper examMapper,
                                    ExamAttemptMapper attemptMapper, PaperMapper paperMapper, PaperQuestionMapper paperQuestionMapper) {
        this.userMapper = userMapper; this.examMapper = examMapper;
        this.attemptMapper = attemptMapper; this.paperMapper = paperMapper; this.paperQuestionMapper = paperQuestionMapper;
    }

    public TeacherStatistics overview(String username, Long teacherId, boolean admin) {
        SysUser current = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (current == null) throw new BusinessException(401, "当前用户不存在");
        Long creatorFilter;
        String scope;
        boolean allTeachers = admin && teacherId == null;
        List<Long> allTeacherIds = List.of();
        if (admin) {
            creatorFilter = teacherId;
            if (teacherId != null) {
                SysUser selected = userMapper.selectById(teacherId);
                if (selected == null || !userMapper.findRoleCodes(teacherId).contains("TEACHER")) throw new BusinessException(404, "指定教师不存在");
                scope = selected.getNickname() == null || selected.getNickname().isBlank() ? selected.getUsername() : selected.getNickname();
            } else {
                scope = "全部教师";
                allTeacherIds = userMapper.findUserIdsByRoleCode("TEACHER");
                if (allTeacherIds.isEmpty()) {
                    return new TeacherStatistics(scope, 0, 0, 0, BigDecimal.ZERO, scoreBands(List.of()), List.of());
                }
            }
        } else {
            creatorFilter = current.getId(); scope = current.getNickname() == null || current.getNickname().isBlank() ? current.getUsername() : current.getNickname();
            if (teacherId != null && !teacherId.equals(current.getId())) throw new BusinessException(403, "教师只能查看自己的统计数据");
        }

        QueryWrapper<Exam> examQuery = new QueryWrapper<Exam>().orderByDesc("id");
        QueryWrapper<Paper> paperQuery = new QueryWrapper<Paper>().orderByDesc("id");
        if (creatorFilter != null) {
            examQuery.eq("creator_id", creatorFilter);
            paperQuery.eq("creator_id", creatorFilter);
        } else if (allTeachers) {
            examQuery.in("creator_id", allTeacherIds);
            paperQuery.in("creator_id", allTeacherIds);
        }
        List<Exam> exams = examMapper.selectList(examQuery);
        List<Paper> papers = paperMapper.selectList(paperQuery);
        Map<Long, Exam> examById = exams.stream().collect(Collectors.toMap(Exam::getId, Function.identity(), (a,b) -> a));

        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        List<ExamAttempt> published = examIds.isEmpty() ? List.of() : attemptMapper.selectList(
                new QueryWrapper<ExamAttempt>().in("exam_id", examIds).eq("status", "PUBLISHED").orderByDesc("submit_time"));
        List<Long> linkedPaperIds = exams.stream().map(Exam::getPaperId).filter(Objects::nonNull).distinct().toList();
        List<Paper> linkedPapers = linkedPaperIds.isEmpty() ? List.of() : paperMapper.selectBatchIds(linkedPaperIds);
        Map<Long, Paper> paperById = linkedPapers.stream().collect(Collectors.toMap(Paper::getId, Function.identity(), (a,b) -> a));
        Map<Long, List<BigDecimal>> ratesByPaper = new HashMap<>();
        List<BigDecimal> allRates = new ArrayList<>();
        for (ExamAttempt attempt : published) {
            Exam exam = examById.get(attempt.getExamId());
            Paper paper = exam == null ? null : paperById.get(exam.getPaperId());
            if (paper == null || paper.getTotalScore() == null || paper.getTotalScore().signum() <= 0 || attempt.getTotalScore() == null) continue;
            BigDecimal rate = attempt.getTotalScore().multiply(BigDecimal.valueOf(100))
                    .divide(paper.getTotalScore(), 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
            allRates.add(rate); ratesByPaper.computeIfAbsent(paper.getId(), ignored -> new ArrayList<>()).add(rate);
        }
        List<TeacherStatistics.PaperDifficulty> difficulties = paperDifficulties(papers, ratesByPaper);
        return new TeacherStatistics(scope, exams.size(), papers.size(), published.size(), average(allRates),
                scoreBands(allRates), difficulties);
    }

    private List<TeacherStatistics.PaperDifficulty> paperDifficulties(List<Paper> papers, Map<Long, List<BigDecimal>> ratesByPaper) {
        if (papers.isEmpty()) return List.of();
        List<Long> ids = papers.stream().map(Paper::getId).toList();
        List<PaperQuestion> allQuestions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>().in("paper_id", ids).orderByAsc("sort"));
        Map<Long, List<PaperQuestion>> questionsByPaper = allQuestions.stream().collect(Collectors.groupingBy(PaperQuestion::getPaperId));
        List<TeacherStatistics.PaperDifficulty> result = new ArrayList<>();
        for (Paper paper : papers) {
            List<PaperQuestion> questions = questionsByPaper.getOrDefault(paper.getId(), List.of());
            List<BigDecimal> rates = ratesByPaper.getOrDefault(paper.getId(), List.of());
            BigDecimal averageRate = average(rates);
            BigDecimal index; String label; String basis;
            if (questions.isEmpty()) {
                index = BigDecimal.ZERO; label = "暂无数据"; basis = "试卷未包含题目";
            } else if (!rates.isEmpty()) {
                index = BigDecimal.valueOf(100).subtract(averageRate).setScale(2, RoundingMode.HALF_UP);
                label = averageRate.compareTo(BigDecimal.valueOf(80)) >= 0 ? "较易" : averageRate.compareTo(BigDecimal.valueOf(60)) >= 0 ? "中等" : "较难";
                basis = "已发布成绩平均得分率";
            } else {
                BigDecimal weighted = BigDecimal.ZERO; BigDecimal totalWeight = BigDecimal.ZERO;
                for (PaperQuestion question : questions) {
                    BigDecimal weight = question.getScore() == null || question.getScore().signum() <= 0 ? BigDecimal.ONE : question.getScore();
                    String difficulty = question.getDifficultySnapshot() == null ? "MEDIUM" : question.getDifficultySnapshot().toUpperCase(Locale.ROOT);
                    BigDecimal value = switch (difficulty) { case "EASY" -> new BigDecimal("0.30"); case "HARD" -> new BigDecimal("0.90"); default -> new BigDecimal("0.60"); };
                    weighted = weighted.add(value.multiply(weight)); totalWeight = totalWeight.add(weight);
                }
                index = totalWeight.signum() == 0 ? BigDecimal.ZERO : weighted.multiply(BigDecimal.valueOf(100)).divide(totalWeight, 2, RoundingMode.HALF_UP);
                label = index.compareTo(BigDecimal.valueOf(40)) <= 0 ? "较易" : index.compareTo(BigDecimal.valueOf(65)) <= 0 ? "中等" : "较难";
                basis = "题目难度等级按分值加权估算";
            }
            result.add(new TeacherStatistics.PaperDifficulty(paper.getId(), paper.getName(), questions.size(),
                    paper.getTotalScore() == null ? BigDecimal.ZERO : paper.getTotalScore(), index, label,
                    rates.isEmpty() ? null : averageRate, basis));
        }
        return result;
    }

    private List<TeacherStatistics.ScoreBand> scoreBands(List<BigDecimal> rates) {
        String[] labels = {"0–59%", "60–69%", "70–79%", "80–89%", "90–100%"};
        long[] counts = new long[5];
        for (BigDecimal value : rates) {
            int index = value.compareTo(BigDecimal.valueOf(60)) < 0 ? 0
                    : value.compareTo(BigDecimal.valueOf(70)) < 0 ? 1
                    : value.compareTo(BigDecimal.valueOf(80)) < 0 ? 2
                    : value.compareTo(BigDecimal.valueOf(90)) < 0 ? 3 : 4;
            counts[index]++;
        }
        List<TeacherStatistics.ScoreBand> bands = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            BigDecimal percent = rates.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(counts[i] * 100.0 / rates.size()).setScale(2, RoundingMode.HALF_UP);
            bands.add(new TeacherStatistics.ScoreBand(labels[i], counts[i], percent));
        }
        return bands;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return BigDecimal.ZERO;
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }
}
