package com.example.exam.grade;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.exam.Answer;
import com.example.exam.exam.AnswerMapper;
import com.example.exam.exam.Exam;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.exam.ExamMapper;
import com.example.exam.paper.PaperQuestion;
import com.example.exam.paper.PaperQuestionMapper;
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
class GradeServiceTest {

    @Mock ExamAttemptMapper attemptMapper;
    @Mock AnswerMapper answerMapper;
    @Mock PaperQuestionMapper paperQuestionMapper;
    @Mock SysUserMapper userMapper;
    @Mock ExamMapper examMapper;
    @Mock WrongQuestionMapper wrongQuestionMapper;

    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(attemptMapper, answerMapper, paperQuestionMapper,
                userMapper, examMapper, wrongQuestionMapper);
    }

    @Test
    void unpublishedStudentResultHidesAllScoreFields() {
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L); attempt.setExamId(10L); attempt.setUserId(20L);
        attempt.setAttemptNo(1); attempt.setStatus("SUBMITTED");
        attempt.setObjectiveScore(new BigDecimal("6"));
        attempt.setSubjectiveScore(new BigDecimal("4"));
        attempt.setTotalScore(new BigDecimal("10"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(attempt));

        List<GradeSummary> results = service.myResults("student");

        assertEquals(1, results.size());
        assertEquals("SUBMITTED", results.get(0).status());
        assertNull(results.get(0).objectiveScore());
        assertNull(results.get(0).subjectiveScore());
        assertNull(results.get(0).totalScore());
    }

    @Test
    void batchPublishRejectsWhenThereAreNoSubmittedAttempts() {
        Exam exam = new Exam(); exam.setId(10L); exam.setCreatorId(3L); exam.setPaperId(100L);
        SysUser admin = new SysUser(); admin.setId(1L); admin.setUsername("admin");
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(admin);
        when(attemptMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.publishAllForExam(10L, "admin", true));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("还没有可发布成绩"));
        verify(attemptMapper, never()).updateById(any(ExamAttempt.class));
    }

    @Test
    void publishedResultForItsOwnerHidesReferenceAnswer() {
        Exam exam = new Exam(); exam.setId(10L); exam.setCreatorId(3L); exam.setPaperId(100L);
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L); attempt.setExamId(10L); attempt.setUserId(20L);
        attempt.setAttemptNo(1); attempt.setStatus("PUBLISHED");
        Answer answer = new Answer();
        answer.setId(40L); answer.setAttemptId(30L); answer.setPaperQuestionId(50L); answer.setAnswerContent("我的作答");
        PaperQuestion question = new PaperQuestion();
        question.setId(50L); question.setQuestionId(60L); question.setQuestionNo(1);
        question.setTypeSnapshot("SHORT_ANSWER"); question.setContentSnapshot("简述题");
        question.setAnswerConfigSnapshot("评分要点：答出关键点即可");
        question.setScore(new BigDecimal("10"));
        when(attemptMapper.selectById(30L)).thenReturn(attempt);
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(userMapper.findRoleCodes(20L)).thenReturn(List.of());
        when(answerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(answer));
        when(paperQuestionMapper.selectBatchIds(any())).thenReturn(List.of(question));

        GradeAttemptDetail detail = service.detail(30L, "student", false);

        assertEquals(1, detail.answers().size());
        assertNull(detail.answers().get(0).referenceAnswer(), "评分要点不能在成绩发布后泄露给学生");
        assertEquals("我的作答", detail.answers().get(0).answerContent());
    }
}
