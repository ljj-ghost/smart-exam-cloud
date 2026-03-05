package com.smart.exam.exam.mapper;

import com.smart.exam.exam.model.read.PaperQuestionSnapshot;
import com.smart.exam.exam.model.read.PaperSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuestionReadMapper {

    @Select("""
            SELECT id, name, total_score AS totalScore, time_limit_minutes AS timeLimitMinutes
            FROM question_db.q_paper
            WHERE id = #{paperId}
            LIMIT 1
            """)
    PaperSnapshot selectPaperById(@Param("paperId") Long paperId);

    @Select("""
            SELECT id, name, total_score AS totalScore, time_limit_minutes AS timeLimitMinutes
            FROM question_db.q_paper
            WHERE id = #{paperId}
              AND created_by = #{createdBy}
            LIMIT 1
            """)
    PaperSnapshot selectPaperByIdAndCreatedBy(@Param("paperId") Long paperId, @Param("createdBy") Long createdBy);

    @Select("""
            SELECT pq.question_id AS questionId,
                   pq.score,
                   pq.order_no AS orderNo,
                   q.type,
                   q.stem,
                   q.options_json AS optionsJson
            FROM question_db.q_paper_question pq
            JOIN question_db.q_question q ON q.id = pq.question_id
            WHERE pq.paper_id = #{paperId}
            ORDER BY pq.order_no ASC, pq.id ASC
            """)
    List<PaperQuestionSnapshot> selectPaperQuestionsByPaperId(@Param("paperId") Long paperId);
}
