package com.example.exam.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.knowledge.KnowledgePoint;
import com.example.exam.knowledge.KnowledgePointMapper;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOption;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.subject.Subject;
import com.example.exam.subject.SubjectMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the demo question bank. Like {@link SeedDataConfig} it is restricted to the
 * {@code dev} profile so demo data can never be injected into a production database.
 */
@Configuration
@Profile("dev")
public class SeedQuestionDataConfig {
    private static final Logger log = LoggerFactory.getLogger(SeedQuestionDataConfig.class);

    @Bean
    CommandLineRunner seedQuestions(QuestionMapper questionMapper,
                                    QuestionOptionMapper optionMapper,
                                    SubjectMapper subjectMapper,
                                    KnowledgePointMapper knowledgePointMapper,
                                    SysUserMapper userMapper) {
        return new OrderedCommandLineRunner(2, args -> {
            if (questionMapper.selectCount(null) > 0) return;

            // The user's auto-increment ID is not guaranteed to be 1. Seed users first,
            // then use a real active account as creator to satisfy fk_question_creator.
            SysUser creator = userMapper.selectOne(new QueryWrapper<SysUser>()
                    .eq("username", "teacher").eq("status", 1).last("LIMIT 1"));
            if (creator == null) {
                creator = userMapper.selectOne(new QueryWrapper<SysUser>()
                        .eq("username", "admin").eq("status", 1).last("LIMIT 1"));
            }
            if (creator == null) {
                creator = userMapper.selectOne(new QueryWrapper<SysUser>()
                        .eq("status", 1).orderByAsc("id").last("LIMIT 1"));
            }
            if (creator == null || creator.getId() == null) {
                log.warn("Skipping demo question seeding because no active sys_user exists; check SeedDataConfig and sys_user data.");
                return;
            }
            final Long creatorId = creator.getId();
            log.info("Seeding demo questions with existing user '{}' (id={})", creator.getUsername(), creatorId);

            Subject java = subjectMapper.selectOne(new QueryWrapper<Subject>().eq("name", "Java程序设计").last("LIMIT 1"));
            if (java == null) return;
            KnowledgePoint oop = knowledgePointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                    .eq("subject_id", java.getId()).eq("name", "面向对象").last("LIMIT 1"));
            KnowledgePoint collection = knowledgePointMapper.selectOne(new QueryWrapper<KnowledgePoint>()
                    .eq("subject_id", java.getId()).eq("name", "集合框架").last("LIMIT 1"));
            if (oop == null || collection == null) return;

            for (int i = 1; i <= 8; i++) {
                Question q = new Question();
                q.setSubjectId(java.getId());
                q.setKnowledgePointId(i % 2 == 0 ? collection.getId() : oop.getId());
                q.setType("SINGLE_CHOICE");
                q.setContent("【演示题" + i + "】Java中用于创建对象的关键字是？");
                q.setAnalysis("new 关键字用于创建类的对象。");
                q.setDifficulty(i <= 3 ? "EASY" : i <= 6 ? "MEDIUM" : "HARD");
                q.setDifficultyValue(i <= 3 ? BigDecimal.valueOf(0.30) : i <= 6 ? BigDecimal.valueOf(0.50) : BigDecimal.valueOf(0.80));
                q.setDefaultScore(BigDecimal.valueOf(2));
                q.setAnswerConfig(null);
                q.setTags("[\"demo\",\"java\"]");
                q.setStatus(1);
                q.setCreatorId(creatorId);
                questionMapper.insert(q);
                saveOptions(optionMapper, q.getId(), List.of(
                        option("A", "new", 1, true), option("B", "class", 2, false),
                        option("C", "this", 3, false), option("D", "super", 4, false)));
            }

            for (int i = 1; i <= 4; i++) {
                Question q = new Question();
                q.setSubjectId(java.getId());
                q.setKnowledgePointId(collection.getId());
                q.setType("MULTIPLE_CHOICE");
                q.setContent("【演示多选题" + i + "】以下属于Java集合接口的是？");
                q.setAnalysis("List、Set、Map均属于集合框架中的核心接口。");
                q.setDifficulty(i <= 2 ? "MEDIUM" : "HARD");
                q.setDifficultyValue(i <= 2 ? BigDecimal.valueOf(0.50) : BigDecimal.valueOf(0.80));
                q.setDefaultScore(BigDecimal.valueOf(3));
                q.setTags("[\"demo\",\"collection\"]");
                q.setStatus(1);
                q.setCreatorId(creatorId);
                questionMapper.insert(q);
                saveOptions(optionMapper, q.getId(), List.of(
                        option("A", "List", 1, true), option("B", "Set", 2, true),
                        option("C", "Map", 3, true), option("D", "Thread", 4, false)));
            }

            for (int i = 1; i <= 4; i++) {
                Question q = new Question();
                q.setSubjectId(java.getId());
                q.setKnowledgePointId(oop.getId());
                q.setType("TRUE_FALSE");
                q.setContent("【演示判断题" + i + "】Java支持方法重载（Overload）。");
                q.setAnalysis("重载要求方法名相同、参数列表不同，与返回值无关。");
                q.setDifficulty("EASY");
                q.setDifficultyValue(BigDecimal.valueOf(0.30));
                q.setDefaultScore(BigDecimal.ONE);
                q.setTags("[\"demo\",\"oop\"]");
                q.setStatus(1);
                q.setCreatorId(creatorId);
                questionMapper.insert(q);
                saveOptions(optionMapper, q.getId(), List.of(
                        option("A", "正确", 1, true), option("B", "错误", 2, false)));
            }

            for (int i = 1; i <= 4; i++) {
                Question q = new Question();
                q.setSubjectId(java.getId());
                q.setKnowledgePointId(oop.getId());
                q.setType("FILL_BLANK");
                q.setContent("【演示填空题" + i + "】Java程序的入口方法是____。");
                q.setAnswerConfig("{\"answers\":[\"main\",\"main()\"]}");
                q.setAnalysis("Java应用程序通常从 main 方法开始执行。");
                q.setDifficulty(i <= 2 ? "EASY" : "MEDIUM");
                q.setDifficultyValue(i <= 2 ? BigDecimal.valueOf(0.30) : BigDecimal.valueOf(0.50));
                q.setDefaultScore(BigDecimal.valueOf(2));
                q.setTags("[\"demo\",\"basic\"]");
                q.setStatus(1);
                q.setCreatorId(creatorId);
                questionMapper.insert(q);
            }
        });
    }

    private void saveOptions(QuestionOptionMapper mapper, Long questionId, List<QuestionOption> options) {
        for (QuestionOption option : options) {
            option.setQuestionId(questionId);
            mapper.insert(option);
        }
    }

    private QuestionOption option(String key, String content, int sort, boolean correct) {
        QuestionOption option = new QuestionOption();
        option.setOptionKey(key);
        option.setOptionContent(content);
        option.setSort(sort);
        option.setIsCorrect(correct ? 1 : 0);
        return option;
    }
}
