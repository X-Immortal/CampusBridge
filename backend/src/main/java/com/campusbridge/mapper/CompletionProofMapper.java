package com.campusbridge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campusbridge.pojo.CompletionProof;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompletionProofMapper extends BaseMapper<CompletionProof> {

    default CompletionProof selectByTaskId(Long taskId) {
        return selectOne(Wrappers.<CompletionProof>lambdaQuery().eq(CompletionProof::getTaskId, taskId)
                .orderByDesc(CompletionProof::getSubmittedAt, CompletionProof::getId).last("LIMIT 1"));
    }

}
