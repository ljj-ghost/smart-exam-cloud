package com.smart.exam.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.analysis.entity.ScoreEntity;
import com.smart.exam.analysis.model.StudentScoreItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScoreMapper extends BaseMapper<ScoreEntity> {

    @Select("""
            <script>
            SELECT
                sc.session_id AS sessionId,
                sc.user_id AS userId,
                su.username AS username,
                su.real_name AS realName,
                sc.total_score AS totalScore,
                sc.created_at AS publishedAt
            FROM analysis_db.a_score sc
            LEFT JOIN user_db.sys_user su ON su.id = sc.user_id
            WHERE sc.exam_id = #{examId}
            <if test="keyword != null and keyword != ''">
              AND (
                CAST(sc.user_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                OR su.username LIKE CONCAT('%', #{keyword}, '%')
                OR su.real_name LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY sc.total_score DESC, sc.created_at ASC, sc.user_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<StudentScoreItem> selectScoreSheet(@Param("examId") Long examId,
                                            @Param("keyword") String keyword,
                                            @Param("limit") Integer limit);
}
