package com.campusbridge.mapper;

import com.campusbridge.pojo.CompletionProof;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CompletionProofMapper {
    int insert(CompletionProof entity);

    CompletionProof selectById(Long id);

    CompletionProof selectByTaskId(Long taskId);

    List<CompletionProof> selectAll();

    int updateById(CompletionProof entity);

    int deleteById(Long id);
}
