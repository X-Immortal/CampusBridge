package com.campusbridge.domain;

/**
 * 一个澄清问题。必须说明「为什么问」——评审时会追问这一句。
 *
 * @param blocksAction 不回答它就不能进入确认（缺时间/地点/报酬），还是只是建议（比如是否要凭证）
 */
public record ClarificationQuestion(
        String field,
        String question,
        String reason,
        int priority,
        boolean blocksAction) {
}
