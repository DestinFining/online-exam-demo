package com.example.exam.paper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.exam.Exam;
import com.example.exam.exam.ExamMapper;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.knowledge.KnowledgePointMapper;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.subject.Subject;
import com.example.exam.subject.SubjectMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperServiceTest {

    @Mock PaperMapper paperMapper;
    @Mock ExamMapper examMapper;
    @Mock ExamAttemptMapper attemptMapper;
    @Mock PaperQuestionMapper paperQuestionMapper;
    @Mock PaperQuestionOptionMapper paperQuestionOptionMapper;
    @Mock QuestionMapper questionMapper;
    @Mock QuestionOptionMapper questionOptionMapper;
    @Mock SubjectMapper subjectMapper;
    @Mock SysUserMapper userMapper;
    @Mock KnowledgePointMapper knowledgePointMapper;

    private PaperService service;

    @BeforeEach
    void setUp() {
        service = new PaperService(paperMapper, examMapper, attemptMapper, paperQuestionMapper, paperQuestionOptionMapper,
                questionMapper, questionOptionMapper, subjectMapper, userMapper,
                knowledgePointMapper, new ObjectMapper());
    }

    @Test
    void ruleGenerationReturnsConflictWhenEasyQuestionPoolIsTooSmall() {
        Subject subject = new Subject(); subject.setId(9L); subject.setName("Java");
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(subjectMapper.selectById(9L)).thenReturn(subject);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                question(1L), question(2L), question(3L)));

        PaperRequest.RuleConfig rule = new PaperRequest.RuleConfig(
                new BigDecimal("12"),
                List.of(new PaperRequest.TypeRule("SINGLE_CHOICE", 6, new BigDecimal("2"))),
                Map.of("EASY", BigDecimal.ONE),
                null);
        PaperRequest request = new PaperRequest("组卷不足测试", 9L, "RULE", 60,
                null, rule);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request, "teacher"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("需要=6"));
        assertTrue(error.getMessage().contains("可用=3"));
        verify(paperQuestionMapper, never()).insert(any(PaperQuestion.class));
    }

    @Test
    void publishedPaperCanBeEditedWhenNoLinkedExamsHaveStarted() {
        Paper paper = new Paper();
        paper.setId(100L); paper.setName("旧名称"); paper.setSubjectId(9L); paper.setType("FIXED");
        paper.setDuration(60); paper.setCreatorId(3L); paper.setStatus("PUBLISHED");
        Subject subject = new Subject(); subject.setId(9L); subject.setName("Java");
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(paperMapper.selectById(100L)).thenReturn(paper);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);
        when(subjectMapper.selectById(9L)).thenReturn(subject);
        Exam futureExam = new Exam();
        futureExam.setId(201L); futureExam.setPaperId(100L); futureExam.setName("尚未开始的考试");
        futureExam.setStatus("PUBLISHED");
        futureExam.setStartTime(LocalDateTime.now().plusDays(1));
        futureExam.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        when(examMapper.selectList(any(Wrapper.class))).thenReturn(List.of(futureExam));
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(questionMapper.selectById(1L)).thenReturn(question(1L));
        when(paperQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(questionOptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PaperRequest request = new PaperRequest("修改后的试卷", 9L, "FIXED", 90,
                List.of(new PaperRequest.FixedQuestionItem(1L, new BigDecimal("5"))), null);

        assertDoesNotThrow(() -> service.update(100L, request, "teacher", false));
        ArgumentCaptor<Paper> updatedPaper = ArgumentCaptor.forClass(Paper.class);
        verify(paperMapper).updateById(updatedPaper.capture());
        assertEquals("修改后的试卷", updatedPaper.getValue().getName());
        assertEquals(90, updatedPaper.getValue().getDuration());
        assertEquals("PUBLISHED", updatedPaper.getValue().getStatus());
        verify(paperQuestionMapper).insert(any(PaperQuestion.class));
    }

    @Test
    void publishedPaperCannotBeEditedAfterLinkedExamHasStarted() {
        Paper paper = new Paper();
        paper.setId(100L); paper.setCreatorId(3L); paper.setStatus("PUBLISHED");
        Exam exam = new Exam();
        exam.setId(200L); exam.setPaperId(100L); exam.setName("进行中的考试");
        exam.setStatus("PUBLISHED"); exam.setStartTime(LocalDateTime.now().minusMinutes(1));
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(paperMapper.selectById(100L)).thenReturn(paper);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);
        when(examMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));

        PaperRequest request = new PaperRequest("修改后的试卷", 9L, "FIXED", 60,
                List.of(new PaperRequest.FixedQuestionItem(1L, new BigDecimal("2"))), null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(100L, request, "teacher", false));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("已经开始或结束"));
        verify(paperQuestionMapper, never()).delete(any(Wrapper.class));
        verify(paperMapper, never()).updateById(any(Paper.class));
    }

    @Test
    void publishedPaperCannotBeEditedWhenFutureExamAlreadyHasAttempts() {
        Paper paper = new Paper(); paper.setId(100L); paper.setCreatorId(3L); paper.setStatus("PUBLISHED");
        Exam exam = new Exam();
        exam.setId(200L); exam.setPaperId(100L); exam.setName("已有答卷的考试");
        exam.setStatus("PUBLISHED"); exam.setStartTime(LocalDateTime.now().plusDays(1));
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(paperMapper.selectById(100L)).thenReturn(paper);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);
        when(examMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));
        when(attemptMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        PaperRequest request = new PaperRequest("修改后的试卷", 9L, "FIXED", 60,
                List.of(new PaperRequest.FixedQuestionItem(1L, new BigDecimal("2"))), null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(100L, request, "teacher", false));
        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("已有答卷"));
        verify(paperQuestionMapper, never()).delete(any(Wrapper.class));
    }

    private Question question(Long id) {
        Question q = new Question();
        q.setId(id); q.setSubjectId(9L); q.setType("SINGLE_CHOICE");
        q.setDifficulty("EASY"); q.setStatus(1); q.setContent("Q" + id);
        q.setDefaultScore(new BigDecimal("2")); q.setAnswerConfig("{\"correct\":\"A\"}");
        return q;
    }

    @Test
    void batchDeleteRejectsPapersReferencedByExams() {
        Paper paper = new Paper(); paper.setId(100L); paper.setCreatorId(3L); paper.setStatus("PUBLISHED");
        Exam exam = new Exam(); exam.setId(200L); exam.setPaperId(100L);
        SysUser teacher = new SysUser(); teacher.setId(3L); teacher.setUsername("teacher");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(teacher);
        when(paperMapper.selectBatchIds(any(java.util.Collection.class))).thenReturn(List.of(paper));
        when(examMapper.selectList(any(Wrapper.class))).thenReturn(List.of(exam));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteBatch(List.of(100L), "teacher", false));

        assertEquals(409, error.getCode());
        verify(paperMapper, never()).deleteById(100L);
    }
}
