package com.campusbridge.mapper;

import com.campusbridge.dto.query.ContentQuery;
import com.campusbridge.dto.view.ContentItemView;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ContentMapper {
    List<ContentItemView> selectPage(ContentQuery filters);

    long count(ContentQuery filters);
}
