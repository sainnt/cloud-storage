package com.sainnt.server.service;

import com.sainnt.server.dto.FileDto;
import com.sainnt.server.dto.request.DownloadFileRequest;
import com.sainnt.server.dto.request.FilesListRequest;
import com.sainnt.server.dto.request.UploadFileRequest;
import com.sainnt.server.service.operations.ByteDownloadOperation;
import com.sainnt.server.service.operations.ByteUploadOperation;
import com.sainnt.server.service.operations.FileDownloadOperation;
import com.sainnt.server.service.operations.FileUploadOperation;

import java.util.List;

public interface FileOperationsService {
    FileUploadOperation uploadFile(UploadFileRequest request);

    FileDownloadOperation downloadFile(DownloadFileRequest request);

    List<FileDto> getFiles(FilesListRequest request);
}
