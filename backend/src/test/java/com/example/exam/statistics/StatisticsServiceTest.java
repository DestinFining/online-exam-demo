package com.example.exam.statistics;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.favorite.FavoriteMapper;
import com.example.exam.practice.PracticeRecordMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock SysUserMapper userMapper;
    @Mock PracticeRecordMapper practiceMapper;
    @Mock WrongQuestionMapper wrongMapper;
    @Mock FavoriteMapper favoriteMapper;
    @Mock ExamAttemptMapper attemptMapper;

    private StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(userMapper, practiceMapper, wrongMapper, favoriteMapper, attemptMapper);
    }

    @Test
    void studentOverviewUsesDatabaseAggregatesAndKnowledgePointGroups() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("student");

        PracticeSummary practice = new PracticeSummary();
        practice.setTotalPractice(1000);
        practice.setCorrectPractice(720);
        practice.setWrongPractice(280);

        KnowledgePointPracticeStats knowledge = new KnowledgePointPracticeStats();
        knowledge.setKnowledgePointId(12L);
        knowledge.setKnowledgePointName("集合与映射");
        knowledge.setAttempts(50);
        knowledge.setCorrect(35);

        ExamScoreSummary scores = new ExamScoreSummary();
        scores.setExamCount(8);
        scores.setAverageScore(new BigDecimal("82.50"));
        scores.setHighestScore(new BigDecimal("98.00"));

        ExamAttempt latest = new ExamAttempt();
        latest.setId(101L); latest.setExamId(200L); latest.setAttemptNo(1);
        latest.setTotalScore(new BigDecimal("98.00"));

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(practiceMapper.selectSummaryByUserId(7L)).thenReturn(practice);
        when(practiceMapper.selectKnowledgePointStatsByUserId(7L)).thenReturn(List.of(knowledge));
        when(wrongMapper.selectCount(any(Wrapper.class))).thenReturn(4L);
        when(favoriteMapper.selectCount(any(Wrapper.class))).thenReturn(9L);
        when(attemptMapper.selectPublishedScoreSummary(7L)).thenReturn(scores);
        when(attemptMapper.selectLatestPublishedScoreTrend(7L)).thenReturn(List.of(latest));

        StudentStatistics result = service.student("student");

        assertEquals(1000, result.totalPractice());
        assertEquals(720, result.correctPractice());
        assertEquals(280, result.wrongPractice());
        assertEquals(new BigDecimal("72.00"), result.practiceAccuracy());
        assertEquals(4, result.wrongQuestionCount());
        assertEquals(9, result.favoriteCount());
        assertEquals(8, result.examCount());
        assertEquals(new BigDecimal("82.50"), result.averageScore());
        assertEquals(new BigDecimal("98.00"), result.highestScore());
        assertEquals(1, result.knowledgePoints().size());
        assertEquals("集合与映射", result.knowledgePoints().get(0).knowledgePointName());
        assertEquals(new BigDecimal("70.00"), result.knowledgePoints().get(0).accuracy());
        assertEquals(1, result.scoreTrend().size());

        // A high-volume history must not be loaded row-by-row into application memory.
        verify(practiceMapper, never()).selectList(any(Wrapper.class));
        verify(practiceMapper).selectSummaryByUserId(7L);
        verify(practiceMapper).selectKnowledgePointStatsByUserId(7L);
        verify(attemptMapper).selectPublishedScoreSummary(7L);
        verify(attemptMapper).selectLatestPublishedScoreTrend(7L);
    }

    @Test
    void studentWithNoPracticeOrPublishedExamsGetsZeroedSummary() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("new-student");

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(practiceMapper.selectSummaryByUserId(8L)).thenReturn(new PracticeSummary());
        when(practiceMapper.selectKnowledgePointStatsByUserId(8L)).thenReturn(List.of());
        when(wrongMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(favoriteMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(attemptMapper.selectPublishedScoreSummary(8L)).thenReturn(new ExamScoreSummary());
        when(attemptMapper.selectLatestPublishedScoreTrend(8L)).thenReturn(List.of());

        StudentStatistics result = service.student("new-student");

        assertEquals(0, result.totalPractice());
        assertEquals(BigDecimal.ZERO, result.practiceAccuracy());
        assertEquals(0, result.examCount());
        assertEquals(BigDecimal.ZERO, result.averageScore());
        assertEquals(BigDecimal.ZERO, result.highestScore());
        assertTrue(result.knowledgePoints().isEmpty());
        assertTrue(result.scoreTrend().isEmpty());
        verify(practiceMapper, never()).selectList(any(Wrapper.class));
    }
}
