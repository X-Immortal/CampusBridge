package com.campusbridge.schema;

import java.util.List;

/** 卡片里的一个选项（比较卡用）。选项必须来自业务数据，不能由模型杜撰。 */
public record ChoiceOption(String id, String label, String detail) {
}
