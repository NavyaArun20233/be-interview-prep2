package com.interviewprep.dto.file;

import org.springframework.core.io.Resource;

/** A stored file's content plus the metadata needed to serve it under its original name. */
public record FileDownload(String originalName, String contentType, Resource content) {}
