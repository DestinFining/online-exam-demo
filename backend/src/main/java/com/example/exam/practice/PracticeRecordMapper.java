package com.example.exam.practice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.exam.statistics.KnowledgePointPracticeStats;
import com.example.exam.statistics.PracticeSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PracticeRecordMapper extends BaseMapper<PracticeRecord> {

    @Select("""
            SELECT COUNT(*) AS total_practice,
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct_practice,
                   COALESCE(SUM(CASE WHEN is_correct = 1 THEN 0 ELSE 1 END), 0) AS wrong_practice
            FROM practice_record
            WHERE user_id = #{userId}
            """)
    @Results(id = "practiceSummaryResult", value = {
            @Result(property = "totalPractice", column = "total_practice"),
            @Result(property = "correctPractice", column = "correct_practice"),
            @Result(property = "wrongPractice", column = "wrong_practice")
    })
    PracticeSummary selectSummaryByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT q.knowledge_point_id AS knowledge_point_id,
                   COALESCE(kp.name, '未知知识点') AS knowledge_point_name,
                   COUNT(*) AS attempts,
                   COALESCE(SUM(CASE WHEN pr.is_correct = 1 THEN 1 ELSE 0 END), 0) AS correct
            FROM practice_record pr
            INNER JOIN question q ON q.id = pr.question_id
            LEFT JOIN knowledge_point kp ON kp.id = q.knowledge_point_id
            WHERE pr.user_id = #{userId}
            GROUP BY q.knowledge_point_id, kp.name
            ORDER BY (SUM(CASE WHEN pr.is_correct = 1 THEN 1 ELSE 0 END) * 1.0 / NULLIF(COUNT(*), 0)) ASC,
                     COUNT(*) DESC
            LIMIT 12
            """)
    @Results(id = "knowledgePointPracticeStatsResult", value = {
            @Result(property = "knowledgePointId", column = "knowledge_point_id"),
            @Result(property = "knowledgePointName", column = "knowledge_point_name"),
            @Result(property = "attempts", column = "attempts"),
            @Result(property = "correct", column = "correct")
    })
    List<KnowledgePointPracticeStats> selectKnowledgePointStatsByUserId(@Param("userId") Long userId);
}
