package com.campusbridge.schema;

/** 确认卡/摘要卡里的一行：字段、展示值、以及这个值是用户说的还是系统推的。 */
public record SummaryRow(String label, String value, String source) {
}
