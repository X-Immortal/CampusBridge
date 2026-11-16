package com.campusbridge.agent;

import com.campusbridge.domain.ClarificationQuestion;
import com.campusbridge.domain.DraftField;
import com.campusbridge.domain.TaskDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 追问 Policy：独立成件，不写在 Prompt 里。
 *
 * <p>判断顺序固定：先问「不补就不能发」的三件事（时间、地点、报酬），再问完成标准，
 * 最后才问风险确认和拆单建议。每次最多问三条，避免一屏塞满问题。</p>
 *
 * <p>每条问题都带 {@code reason}：评审时老师可以指着问「为什么问这个」，答案就在数据里。</p>
 */
@Component
public class ClarificationPolicy {

    private static final int MAX_QUESTIONS_PER_CARD = 3;

    public List<ClarificationQuestion> questionsFor(TaskDraft draft) {
        List<ClarificationQuestion> questions = new ArrayList<>();

        if (!draft.has(DraftField.DEADLINE)) {
            String vague = draft.text(DraftField.TIME_WINDOW);
            String text = vague == null || vague.isBlank()
                    ? "最晚什么时候要送到？"
                    : vague + "几点前要送到？（给我一个具体时间，别人才能判断来不来得及）";
            questions.add(new ClarificationQuestion(
                    DraftField.DEADLINE.key(), text,
                    "没有截止时间，接单的人无法判断能不能完成，任务也无法排序", 1, true));
        }
        if (!draft.has(DraftField.LOCATION)) {
            questions.add(new ClarificationQuestion(
                    DraftField.LOCATION.key(),
                    "去哪里取？给我楼栋或驿站名字就行（不用写宿舍号）。",
                    "没有取件地点，任务无法被履行；精确到宿舍号会暴露隐私，所以只问楼栋", 1, true));
        }
        if (!draft.has(DraftField.REWARD_TYPE)) {
            questions.add(new ClarificationQuestion(
                    DraftField.REWARD_TYPE.key(),
                    "打算怎么答谢？现金 / 积分 / 请喝一杯东西 / 免费，选一个。",
                    "没有报酬形态，比较卡无法算出代价，任务也容易被当成白使唤", 2, true));
        } else {
            String rewardType = draft.text(DraftField.REWARD_TYPE);
            if (("CASH".equalsIgnoreCase(rewardType) || "POINTS".equalsIgnoreCase(rewardType))
                    && draft.integer(DraftField.REWARD_AMOUNT) == null) {
                questions.add(new ClarificationQuestion(
                        DraftField.REWARD_AMOUNT.key(),
                        "给多少？写个数字就行。",
                        "报酬形态是 " + rewardType + "，但没有数量，既没法结算也没法比较", 2, true));
            }
            if ("NEGOTIABLE".equalsIgnoreCase(rewardType)
                    && (draft.text(DraftField.REWARD_DESCRIPTION) == null
                        || draft.text(DraftField.REWARD_DESCRIPTION).isBlank())) {
                questions.add(new ClarificationQuestion(
                        DraftField.REWARD_DESCRIPTION.key(),
                        "怎么答谢？比如“请喝一杯咖啡”。",
                        "面议报酬必须写清具体形式，否则没人知道能拿到什么", 2, true));
            }
        }
        if (!draft.has(DraftField.COMPLETION_STANDARD)) {
            questions.add(new ClarificationQuestion(
                    DraftField.COMPLETION_STANDARD.key(),
                    "怎样算完成？比如“放到 5 号楼一层货架并拍照”。",
                    "没有验收标准，双方在“算不算完成”上一定会扯皮", 2, true));
        }
        if ("HIGH".equalsIgnoreCase(draft.text(DraftField.RISK_LEVEL))
                && !Boolean.TRUE.equals(draft.flag(DraftField.RISK_DISCLOSURE_CONFIRMED))) {
            questions.add(new ClarificationQuestion(
                    DraftField.RISK_DISCLOSURE_CONFIRMED.key(),
                    "这次在深夜/涉及宿舍，风险提示你看过了吗？（回复“是”即可）",
                    "高风险任务必须先披露风险，否则不允许发布（约束 K05）", 3, true));
        }
        Integer itemCount = draft.integer(DraftField.ITEM_COUNT);
        if (itemCount != null && itemCount > 3 && itemCount <= 5) {
            questions.add(new ClarificationQuestion(
                    DraftField.ITEM_COUNT.key(),
                    "一次取 " + itemCount + " 件有点多，要不要拆成两单？（回复件数即可）",
                    "超过 3 件已经接近搬运，报酬和风险都不同；拆单更容易被接", 4, false));
        }

        return questions.stream()
                .sorted(Comparator.comparingInt(ClarificationQuestion::priority))
                .limit(MAX_QUESTIONS_PER_CARD)
                .toList();
    }
}
