package com.knowflow.mapper;

import com.knowflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser findByUsername(@Param("username") String username);

    SysUser findById(@Param("id") Long id);

    int insert(SysUser user);
}
