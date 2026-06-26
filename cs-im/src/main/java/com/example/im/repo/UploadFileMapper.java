package com.example.im.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.im.domain.UploadFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadFileMapper extends BaseMapper<UploadFile> {
}