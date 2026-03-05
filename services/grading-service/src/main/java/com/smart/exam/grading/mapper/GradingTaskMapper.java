package com.smart.exam.grading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.exam.grading.entity.GradingTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GradingTaskMapper extends BaseMapper<GradingTaskEntity> {

    @Select("""
            <script>
            SELECT gt.*
            FROM grading_db.g_grading_task gt
            INNER JOIN exam_db.e_exam ee ON ee.id = gt.exam_id
            WHERE ee.created_by = #{teacherId}
            <if test="status != null and status != ''">
              AND gt.status = #{status}
            </if>
            ORDER BY gt.created_at DESC, gt.id DESC
            </script>
            """)
    List<GradingTaskEntity> selectTeacherTasks(@Param("teacherId") Long teacherId,
                                               @Param("status") String status);
}
