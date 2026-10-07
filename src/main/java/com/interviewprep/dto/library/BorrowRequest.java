package com.interviewprep.dto.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Members are identified by name on the loan; there is no separate member resource. */
public record BorrowRequest(@NotBlank @Size(max = 100) String memberName) {}
