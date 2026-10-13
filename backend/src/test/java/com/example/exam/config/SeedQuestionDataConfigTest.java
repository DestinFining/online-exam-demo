package com.example.exam.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.exam.knowledge.KnowledgePoint;
import com.example.exam.knowledge.KnowledgePointMapper;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.subject.Subject;
import com.example.exam.subject.SubjectMapper;
import com.example.exam.system.role.SysRoleMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.system.user.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class SeedQuestionDataConfigTest {

    @Test
    void seedRunnersAreOrderedUsersBeforeQuestions() {
        SeedDataConfig userConfig = new SeedDataConfig();
        CommandLineRunner userRunner = userConfig.seedUsers(
                mock(SysUserMapper.class), mock(SysRoleMapper.class),
                mock(SysUserRoleMapper.class), mock(PasswordEncoder.class));

        SeedQuestionDataConfig questionConfig = new SeedQuestionDataConfig();
        CommandLineRunner questionRunner = questionConfig.seedQuestions(
                mock(QuestionMapper.class), mock(QuestionOptionMapper.class),
                mock(SubjectMapper.class), mock(KnowledgePointMapper.class),
                mock(SysUserMapper.class));

        assertInstanceOf(Ordered.class, userRunner);
        assertInstanceOf(Ordered.class, questionRunner);
        assertEquals(1, ((Ordered) userRunner).getOrder());
        assertEquals(2, ((Ordered) questionRunner).getOrder());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void demoQuestionsUseAnExistingTeacherIdInsteadOfAssumingIdOne() throws Exception {
        QuestionMapper questions = mock(QuestionMapper.class);
        QuestionOptionMapper options = mock(QuestionOptionMapper.class);
        SubjectMapper subjects = mock(SubjectMapper.class);
        KnowledgePointMapper knowledgePoints = mock(KnowledgePointMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);

        when(questions.selectCount(isNull())).thenReturn(0L);
        SysUser teacher = new SysUser();
        teacher.setId(42L);
        teacher.setUsername("teacher");
        teacher.setStatus(1);
        when(users.selectOne(any(Wrapper.class))).thenReturn(teacher);

        Subject java = new Subject();
        java.setId(7L);
        java.setName("Java程序设计");
        when(subjects.selectOne(any(Wrapper.class))).thenReturn(java);

        KnowledgePoint oop = new KnowledgePoint();
        oop.setId(11L);
        oop.setSubjectId(7L);
        oop.setName("面向对象");
        KnowledgePoint collection = new KnowledgePoint();
        collection.setId(12L);
        collection.setSubjectId(7L);
        collection.setName("集合框架");
        when(knowledgePoints.selectOne(any(Wrapper.class))).thenReturn(oop, collection);

        SeedQuestionDataConfig config = new SeedQuestionDataConfig();
        config.seedQuestions(questions, options, subjects, knowledgePoints, users).run();

        var captor = org.mockito.ArgumentCaptor.forClass(Question.class);
        verify(questions, times(20)).insert(captor.capture());
        assertEquals(20, captor.getAllValues().size());
        captor.getAllValues().forEach(question -> assertEquals(42L, question.getCreatorId()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missingActiveUserSkipsDemoQuestionsWithoutCrashingStartupRunner() throws Exception {
        QuestionMapper questions = mock(QuestionMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        when(questions.selectCount(isNull())).thenReturn(0L);
        when(users.selectOne(any(Wrapper.class))).thenReturn(null);
        SubjectMapper subjects = mock(SubjectMapper.class);
        KnowledgePointMapper knowledgePoints = mock(KnowledgePointMapper.class);

        new SeedQuestionDataConfig().seedQuestions(
                questions, mock(QuestionOptionMapper.class), subjects, knowledgePoints, users).run();

        verify(questions, never()).insert(any(Question.class));
        verifyNoInteractions(subjects, knowledgePoints);
    }
}
