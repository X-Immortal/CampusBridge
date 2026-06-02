package com.campusbridge.dto.enums;

public final class ApiEnums {
    private ApiEnums() {
    }

    public enum UserRole {
        USER, ADMIN
    }

    public enum VerificationMethod {
        STUDENT_CARD_IMAGE, CAMPUS_EMAIL, MANUAL_REVIEW
    }

    public enum VerificationStatus {
        UNVERIFIED, PENDING, VERIFIED, REJECTED
    }

    public enum ContentItemType {
        POST, TASK
    }

    public enum PostCategory {
        INQUIRY_HELP, SECOND_HAND, TEAM_FRIEND, CAMPUS_SOCIAL
    }

    public enum PostStatus {
        DRAFT, PUBLISHED, PENDING_REVIEW, HIDDEN, CLOSED, DELETED, REJECTED
    }

    public enum PostInteractionType {
        LIKE, MARK
    }

    public enum TaskCategory {
        ERRAND, AGENCY, ATTENDANCE_ASSIST
    }

    public enum RewardType {
        FREE, POINTS, CASH, NEGOTIABLE
    }

    public enum TaskStatus {
        DRAFT, OPEN, PENDING_REVIEW, ACCEPTED, IN_PROGRESS,
        PENDING_CONFIRMATION, COMPLETED, REVIEWED, CANCELLED, DISPUTED, REJECTED
    }

    public enum ApplicationStatus {
        PENDING, ACCEPTED, REJECTED, WITHDRAWN
    }

    public enum ConversationType {
        PRIVATE, SYSTEM, TASK
    }

    public enum MessageType {
        TEXT, IMAGE, SYSTEM_NOTIFICATION
    }

    public enum AttachmentOwnerType {
        POST, REPLY, TASK, COMPLETION_PROOF, MESSAGE, REPORT, VERIFICATION
    }

    public enum AttachmentType {
        IMAGE, FILE
    }

    public enum AttachmentStatus {
        TEMPORARY, BOUND, DELETED
    }

    public enum ReportTargetType {
        POST, REPLY, TASK, USER, MESSAGE
    }

    public enum ModerationStatus {
        PENDING, PROCESSING, APPROVED, REJECTED, RESOLVED, PUNISHED
    }

    public enum TaskStatusTransitionAction {
        START, CONFIRM_COMPLETION, REJECT_COMPLETION, DISPUTE, CANCEL
    }
}
