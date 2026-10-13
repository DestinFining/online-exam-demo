package com.example.exam.practice;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOption;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestion;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock QuestionMapper questionMapper;
    @Mock QuestionOptionMapper optionMapper;
    @Mock SysUserMapper userMapper;
    @Mock PracticeRecordMapper recordMapper;
    @Mock WrongQuestionMapper wrongQuestionMapper;

    private PracticeService service;

    @BeforeEach
    void setUp() {
        service = new PracticeService(questionMapper, optionMapper, userMapper,
                recordMapper, wrongQuestionMapper, new ObjectMapper());
    }

    @Test
    void blankTypeAndDifficultyUseObjectiveQuestionDefaults() {
        Question question = question(11L, "SINGLE_CHOICE");
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(optionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<PracticeQuestionView> result = service.start(
                new PracticeStartRequest(null, null, "", "", 10, "RANDOM"));

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).id());
    }

    @Test
    void availableQuestionCountUsesMatchingFiltersAndReturnsDatabaseCount() {
        when(questionMapper.selectCount(any(Wrapper.class))).thenReturn(42L);

        long count = service.countAvailable(1L, 8L, "", "EASY");

        assertEquals(42L, count);
        verify(questionMapper).selectCount(any(Wrapper.class));
    }

    @Test
    void nullTypeAndDifficultyDoNotFailWhenCountingAvailableQuestions() {
        when(questionMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        long count = service.countAvailable(1L, 8L, null, null);

        assertEquals(3L, count);
        verify(questionMapper).selectCount(any(Wrapper.class));
    }

    @Test
    void subjectiveQuestionTypesHaveZeroAutoGradablePracticeCount() {
        assertEquals(0L, service.countAvailable(1L, 8L, "SHORT_ANSWER", null));
        assertEquals(0L, service.countAvailable(1L, 8L, "PROGRAMMING", null));
        verify(questionMapper, never()).selectCount(any(Wrapper.class));
    }

    @Test
    void startingPracticeWithNoEligibleQuestionsReturnsNotFoundBusinessError() {
        when(questionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.start(new PracticeStartRequest(null, null, null, null, 10, "RANDOM")));

        assertEquals(404, error.getCode());
        assertTrue(error.getMessage().contains("没有符合条件的练习题"));
    }

    @Test
    void explicitSubjectivePracticeFilterReturnsClearConflict() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.start(new PracticeStartRequest(null, null, "SHORT_ANSWER", null, 10, "RANDOM")));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("由教师阅卷"));
        verify(questionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void correctSingleChoiceAnswerIsPersistedAsCorrect() {
        SysUser user = new SysUser(); user.setId(7L); user.setUsername("student");
        Question question = question(11L, "SINGLE_CHOICE");
        QuestionOption correct = new QuestionOption();
        correct.setOptionKey("A"); correct.setIsCorrect(1);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(optionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(correct));
        when(wrongQuestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        PracticeResult result = service.answer(new PracticeAnswerRequest(11L, "a", "RANDOM"), "student");

        assertTrue(result.correct());
        ArgumentCaptor<PracticeRecord> recordCaptor = ArgumentCaptor.forClass(PracticeRecord.class);
        verify(recordMapper).insert(recordCaptor.capture());
        assertEquals(1, recordCaptor.getValue().getIsCorrect());
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    @Test
    void subjectivePracticeAnswerIsNotSilentlyRecordedAsWrong() {
        SysUser user = new SysUser(); user.setId(7L); user.setUsername("student");
        Question question = question(22L, "SHORT_ANSWER");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(questionMapper.selectById(22L)).thenReturn(question);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.answer(new PracticeAnswerRequest(22L, "A considered explanation", "RANDOM"), "student"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("主观题"));
        verify(recordMapper, never()).insert(any(PracticeRecord.class));
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    @Test
    void unsupportedQuestionTypeDoesNotGetSilentlyRecordedAsWrong() {
        SysUser user = new SysUser(); user.setId(7L); user.setUsername("student");
        Question question = question(23L, "ESSAY");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(questionMapper.selectById(23L)).thenReturn(question);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.answer(new PracticeAnswerRequest(23L, "a reasonable response", "RANDOM"), "student"));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("不支持自动判分"));
        verify(recordMapper, never()).insert(any(PracticeRecord.class));
        verify(wrongQuestionMapper, never()).insert(any(WrongQuestion.class));
    }

    private Question question(Long id, String type) {
        Question q = new Question();
        q.setId(id); q.setType(type); q.setStatus(1); q.setSubjectId(1L);
        q.setContent("test question"); q.setDifficulty("EASY"); q.setDefaultScore(new BigDecimal("2"));
        return q;
    }
}
