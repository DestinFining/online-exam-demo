package com.example.exam.statistics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.favorite.Favorite;
import com.example.exam.favorite.FavoriteMapper;
import com.example.exam.practice.PracticeRecordMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestion;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class StatisticsService {

    private final SysUserMapper userMapper;
    private final PracticeRecordMapper practiceMapper;
    private final WrongQuestionMapper wrongMapper;
    private final FavoriteMapper favoriteMapper;
    private final ExamAttemptMapper attemptMapper;

    public StatisticsService(
            SysUserMapper userMapper,
            PracticeRecordMapper practiceMapper,
            WrongQuestionMapper wrongMapper,
            FavoriteMapper favoriteMapper,
            ExamAttemptMapper attemptMapper) {
        this.userMapper = userMapper;
        this.practiceMapper = practiceMapper;
        this.wrongMapper = wrongMapper;
        this.favoriteMapper = favoriteMapper;
        this.attemptMapper = attemptMapper;
    }

    public StudentStatistics student(String username) {
        SysUser user = requireUser(username);
        Long userId = user.getId();

        // Aggregate counts in SQL instead of loading every practice record into memory.
        PracticeSummary practiceSummary = practiceMapper.selectSummaryByUserId(userId);
        if (practiceSummary == null) {
            practiceSummary = new PracticeSummary();
        }
        long total = practiceSummary.getTotalPractice();
        long correct = practiceSummary.getCorrectPractice();
        long wrong = practiceSummary.getWrongPractice();
        BigDecimal accuracy = rate(correct, total);

        long wrongCount = wrongMapper.selectCount(
                new QueryWrapper<WrongQuestion>().eq("user_id", userId));
        long favoriteCount = favoriteMapper.selectCount(
                new QueryWrapper<Favorite>().eq("user_id", userId));

        // Count/AVG/MAX are calculated by the database. Only the latest ten scores
        // are fetched to build the student's score-trend chart.
        ExamScoreSummary scoreSummary = attemptMapper.selectPublishedScoreSummary(userId);
        if (scoreSummary == null) {
            scoreSummary = new ExamScoreSummary();
        }
        List<ExamAttempt> recentExams = attemptMapper.selectLatestPublishedScoreTrend(userId);
        if (recentExams == null) {
            recentExams = List.of();
        }

        List<StudentStatistics.ScoreTrend> scoreTrend = recentExams.stream()
                .map(exam -> new StudentStatistics.ScoreTrend(
                        exam.getId(), exam.getExamId(), exam.getAttemptNo(), nonNull(exam.getTotalScore())))
                .toList();

        List<KnowledgePointPracticeStats> groupedKnowledge = practiceMapper
                .selectKnowledgePointStatsByUserId(userId);
        if (groupedKnowledge == null) {
            groupedKnowledge = List.of();
        }
        List<StudentStatistics.KnowledgeMetric> knowledgeMetrics = groupedKnowledge.stream()
                .map(row -> new StudentStatistics.KnowledgeMetric(
                        row.getKnowledgePointId(),
                        row.getKnowledgePointName() == null ? "未知知识点" : row.getKnowledgePointName(),
                        row.getAttempts(),
                        row.getCorrect(),
                        rate(row.getCorrect(), row.getAttempts())))
                .toList();

        return new StudentStatistics(
                total,
                correct,
                wrong,
                accuracy,
                wrongCount,
                favoriteCount,
                scoreSummary.getExamCount(),
                nonNull(scoreSummary.getAverageScore()),
                nonNull(scoreSummary.getHighestScore()),
                knowledgeMetrics,
                scoreTrend);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private SysUser requireUser(String username) {
        SysUser user = userMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(401, "当前用户不存在");
        }
        return user;
    }
}
