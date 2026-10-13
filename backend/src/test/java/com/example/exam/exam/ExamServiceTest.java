package com.example.exam.exam;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.paper.PaperMapper;
import com.example.exam.paper.PaperQuestion;
import com.example.exam.paper.PaperQuestionMapper;
import com.example.exam.paper.PaperQuestionOption;
import com.example.exam.paper.PaperQuestionOptionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock ExamMapper examMapper;
    @Mock ExamAttemptMapper attemptMapper;
    @Mock AnswerMapper answerMapper;
    @Mock ExamEventMapper eventMapper;
    @Mock ExamCandidateMapper candidateMapper;
    @Mock PaperMapper paperMapper;
    @Mock PaperQuestionMapper paperQuestionMapper;
    @Mock PaperQuestionOptionMapper paperQuestionOptionMapper;
    @Mock SysUserMapper userMapper;
    @Mock WrongQuestionMapper wrongQuestionMapper;

    private ExamService service;

    @BeforeEach
    void setUp() {
        service = new ExamService(examMapper, attemptMapper, answerMapper, eventMapper,
                candidateMapper, paperMapper, paperQuestionMapper, paperQuestionOptionMapper,
                userMapper, wrongQuestionMapper, new ObjectMapper());
    }

    @Test
    void studentCannotStartDraftExam() {
        Exam exam = new Exam(); exam.setId(10L); exam.setStatus("DRAFT"); exam.setCandidateScope("ALL");
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.start(10L, "student"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("不可参加"));
        verify(attemptMapper, never()).insert(any(ExamAttempt.class));
    }

    @Test
    void objectiveOnlyExamPublishesResultImmediatelyAfterSubmit() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setPaperId(100L); exam.setDuration(60);
        exam.setEndTime(LocalDateTime.now().plusHours(2));
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L); attempt.setExamId(10L); attempt.setUserId(20L);
        attempt.setStatus("IN_PROGRESS"); attempt.setStartTime(LocalDateTime.now().minusMinutes(5));
        attempt.setVersionNo(0L); attempt.setAttemptNo(1);

        PaperQuestion question = new PaperQuestion();
        question.setId(40L); question.setPaperId(100L); question.setQuestionId(50L);
        question.setQuestionNo(1); question.setTypeSnapshot("SINGLE_CHOICE");
        question.setScore(new BigDecimal("5"));
        PaperQuestionOption correctOption = new PaperQuestionOption();
        correctOption.setOptionKey("A"); correctOption.setIsCorrect(1);

        Answer savedAnswer = new Answer();
        savedAnswer.setId(60L); savedAnswer.setAttemptId(30L); savedAnswer.setPaperQuestionId(40L);
        savedAnswer.setAnswerContent("\"A\""); savedAnswer.setAutoScore(BigDecimal.ZERO);
        savedAnswer.setManualScore(BigDecimal.ZERO); savedAnswer.setFinalScore(BigDecimal.ZERO);

        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectById(30L)).thenReturn(attempt);
        when(paperQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(answerMapper.selectOne(any(Wrapper.class))).thenReturn(null, savedAnswer);
        when(paperQuestionOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(correctOption));
        when(attemptMapper.update(any(ExamAttempt.class), any(Wrapper.class))).thenReturn(1);
        when(wrongQuestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        SubmitResponse result = service.submit(10L, 30L,
                List.of(new SaveAnswerRequest.AnswerItem(40L, "A")), "student");

        assertEquals("PUBLISHED", result.status());
        assertFalse(result.manualGradingRequired());
        assertEquals(new BigDecimal("5"), result.objectiveScore());
        assertEquals(new BigDecimal("5"), result.totalScore());
        assertEquals("PUBLISHED", attempt.getStatus());
    }

    @Test
    void examWithSubjectiveQuestionStillRequiresManualGrading() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setPaperId(100L); exam.setDuration(60);
        exam.setEndTime(LocalDateTime.now().plusHours(2));
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L); attempt.setExamId(10L); attempt.setUserId(20L);
        attempt.setStatus("IN_PROGRESS"); attempt.setStartTime(LocalDateTime.now().minusMinutes(5));
        attempt.setVersionNo(0L); attempt.setAttemptNo(1);

        PaperQuestion question = new PaperQuestion();
        question.setId(40L); question.setPaperId(100L); question.setQuestionId(50L);
        question.setQuestionNo(1); question.setTypeSnapshot("SHORT_ANSWER");
        question.setScore(new BigDecimal("5"));
        Answer savedAnswer = new Answer();
        savedAnswer.setId(60L); savedAnswer.setAttemptId(30L); savedAnswer.setPaperQuestionId(40L);
        savedAnswer.setAnswerContent("\"A longer response\""); savedAnswer.setAutoScore(BigDecimal.ZERO);
        savedAnswer.setManualScore(BigDecimal.ZERO); savedAnswer.setFinalScore(BigDecimal.ZERO);

        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectById(30L)).thenReturn(attempt);
        when(paperQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(answerMapper.selectOne(any(Wrapper.class))).thenReturn(null, savedAnswer);
        when(attemptMapper.update(any(ExamAttempt.class), any(Wrapper.class))).thenReturn(1);

        SubmitResponse result = service.submit(10L, 30L,
                List.of(new SaveAnswerRequest.AnswerItem(40L, "A longer response")), "student");

        assertEquals("SUBMITTED", result.status());
        assertTrue(result.manualGradingRequired());
        assertEquals(BigDecimal.ZERO, result.totalScore());
        assertEquals("SUBMITTED", attempt.getStatus());
    }

    @Test
    void repeatedSubmitIsIdempotentAfterAttemptHasBeenSubmitted() {
        Exam exam = new Exam(); exam.setId(10L); exam.setPaperId(100L); exam.setDuration(60);
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(30L); attempt.setExamId(10L); attempt.setUserId(20L);
        attempt.setStatus("SUBMITTED"); attempt.setObjectiveScore(new BigDecimal("4"));
        attempt.setSubjectiveScore(BigDecimal.ZERO); attempt.setTotalScore(new BigDecimal("4"));
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectById(30L)).thenReturn(attempt);

        SubmitResponse result = service.submit(10L, 30L, List.of(), "student");

        assertEquals(30L, result.attemptId());
        assertEquals("SUBMITTED", result.status());
        assertTrue(result.manualGradingRequired());
        verify(answerMapper, never()).insert(any(Answer.class));
        verify(answerMapper, never()).updateById(any(Answer.class));
    }

    @Test
    void deleteEndedExamArchivesAndPreservesHistory() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setCreatorId(3L); exam.setStatus("PUBLISHED");
        exam.setEndTime(LocalDateTime.now().minusMinutes(1));
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);

        service.deleteEnded(10L, "teacher", false);

        assertEquals("ARCHIVED", exam.getStatus());
        verify(examMapper).updateById(exam);
    }

    @Test
    void cannotDeleteExamBeforeItsEndTime() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setCreatorId(3L); exam.setStatus("PUBLISHED");
        exam.setEndTime(LocalDateTime.now().plusMinutes(30));
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteEnded(10L, "teacher", false));

        assertEquals(409, error.getCode());
        verify(examMapper, never()).updateById(any(Exam.class));
    }

    @Test
    void concurrentStartReusesTheAttemptCreatedByTheOtherRequest() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setCreatorId(3L); exam.setStatus("PUBLISHED");
        exam.setCandidateScope("ALL"); exam.setDuration(60); exam.setPaperId(100L);
        exam.setMaxAttempts(3);
        exam.setStartTime(LocalDateTime.now().minusMinutes(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        ExamAttempt created = new ExamAttempt();
        created.setId(31L); created.setExamId(10L); created.setUserId(20L);
        created.setAttemptNo(1); created.setStatus("IN_PROGRESS");
        created.setStartTime(LocalDateTime.now().minusSeconds(5));
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectOne(any(Wrapper.class))).thenReturn(null, created);
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(attemptMapper.insert(any(ExamAttempt.class)))
                .thenThrow(new DuplicateKeyException("uk_attempt_exam_user_no"));
        when(paperQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ExamResponse.AttemptResponse response = service.start(10L, "student");

        assertEquals(31L, response.attempt().getId());
        verify(attemptMapper, times(1)).insert(any(ExamAttempt.class));
    }

    @Test
    void concurrentStartWithoutAnyVisibleAttemptReportsAConflict() {
        Exam exam = new Exam();
        exam.setId(10L); exam.setCreatorId(3L); exam.setStatus("PUBLISHED");
        exam.setCandidateScope("ALL"); exam.setDuration(60); exam.setPaperId(100L);
        exam.setMaxAttempts(3);
        exam.setStartTime(LocalDateTime.now().minusMinutes(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        SysUser student = new SysUser(); student.setId(20L); student.setUsername("student");
        when(examMapper.selectById(10L)).thenReturn(exam);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(student);
        when(attemptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(attemptMapper.insert(any(ExamAttempt.class)))
                .thenThrow(new DuplicateKeyException("uk_attempt_exam_user_no"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.start(10L, "student"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("冲突"));
    }
}
