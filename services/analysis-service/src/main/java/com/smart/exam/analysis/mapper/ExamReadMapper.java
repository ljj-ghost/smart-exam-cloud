package com.smart.exam.analysis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExamReadMapper {

    @Select("""
            SELECT created_by
            FROM exam_db.e_exam
            WHERE id = #{examId}
            LIMIT 1
            """)
    Long selectExamOwnerById(@Param("examId") Long examId);
}

