package com.campusbridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("completion_proofs")
public class CompletionProof {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long submitterId;
    private String description;
    private LocalDateTime submittedAt;
}
