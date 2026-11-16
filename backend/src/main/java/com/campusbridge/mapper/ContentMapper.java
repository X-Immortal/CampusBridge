package com.campusbridge.mapper;

import com.campusbridge.dto.view.ViewDtos;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ContentMapper {
    List<ViewDtos.ContentItemView> selectPage(Map<String, Object> filters);

    long count(Map<String, Object> filters);
}
