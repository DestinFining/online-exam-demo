package com.example.exam.exam;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.exam.statistics.ExamScoreSummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ExamAttemptMapper extends BaseMapper<ExamAttempt> {

    @Select("""
            SELECT COUNT(*) AS exam_count,
                   COALESCE(AVG(total_score), 0) AS average_score,
                   COALESCE(MAX(total_score), 0) AS highest_score
            FROM exam_attempt
            WHERE user_id = #{userId}
              AND status = 'PUBLISHED'
            """)
    @Results(id = "examScoreSummaryResult", value = {
            @Result(property = "examCount", column = "exam_count"),
            @Result(property = "averageScore", column = "average_score"),
            @Result(property = "highestScore", column = "highest_score")
    })
    ExamScoreSummary selectPublishedScoreSummary(@Param("userId") Long userId);

    @Select("""
            SELECT id, exam_id, user_id, attempt_no, start_time, submit_time, status,
                   objective_score, subjective_score, total_score, cheat_count,
                   last_saved_at, version_no, created_at, updated_at
            FROM exam_attempt
            WHERE user_id = #{userId}
              AND status = 'PUBLISHED'
            ORDER BY submit_time DESC
            LIMIT 10
            """)
    List<ExamAttempt> selectLatestPublishedScoreTrend(@Param("userId") Long userId);
}
