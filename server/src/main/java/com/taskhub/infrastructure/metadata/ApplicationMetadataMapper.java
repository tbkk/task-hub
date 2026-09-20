package com.taskhub.infrastructure.metadata;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationMetadataMapper {

    @Select("SELECT meta_value FROM application_metadata WHERE meta_key = #{key}")
    String findValue(@Param("key") String key);
}
