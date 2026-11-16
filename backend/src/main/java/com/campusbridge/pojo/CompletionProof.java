package com.campusbridge.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
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
