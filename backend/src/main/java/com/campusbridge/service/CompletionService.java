package com.campusbridge.service;

import com.campusbridge.dto.request.RequestDtos;
import com.campusbridge.pojo.CompletionProof;

public interface CompletionService {
    /**
     * 提交完成凭证
     * @param submitterId 提交者ID（必须是任务的provider）
     * @param taskId 任务ID
     * @param request 提交请求
     * @return 完成凭证
     */
    CompletionProof submitCompletionProof(Long submitterId, Long taskId, RequestDtos.SubmitCompletionRequest request);

    /**
     * 确认完成（Requester操作）
     * @param requesterId 请求者ID
     * @param taskId 任务ID
     */
    void confirmCompletion(Long requesterId, Long taskId);

    /**
     * 驳回完成（Requester操作）
     * @param requesterId 请求者ID
     * @param taskId 任务ID
     * @param reason 驳回原因（必填）
     */
    void rejectCompletion(Long requesterId, Long taskId, String reason);

    /**
     * 发起争议（任何一方）
     * @param participantId 参与者ID（requester或provider）
     * @param taskId 任务ID
     * @param reason 争议原因（必填）
     */
    void disputeCompletion(Long participantId, Long taskId, String reason);
}
