package com.smart.exam.exam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserReadMapper {

    @Select({
            "<script>",
            "SELECT id",
            "FROM user_db.sys_user",
            "WHERE role = 'STUDENT'",
            "  AND status = 1",
            "  AND id IN",
            "  <foreach collection='studentIds' item='id' open='(' separator=',' close=')'>",
            "    #{id}",
            "  </foreach>",
            "</script>"
    })
    List<Long> selectActiveStudentIds(@Param("studentIds") List<Long> studentIds);
}

