package com._team._team.approval.service;

import com._team._team.approval.domain.ApprovalRequest;
import com._team._team.approval.domain.Attachment;
import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.dto.resdto.AttachmentResDto;
import com._team._team.approval.repository.ApprovalRequestRepository;
import com._team._team.approval.repository.AttachmentRepository;
import com._team._team.dto.BusinessException;
import com._team._team.s3.S3Uploader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final S3Uploader s3Uploader;
    private final S3Presigner s3Presigner;

    @Autowired
    public AttachmentService(AttachmentRepository attachmentRepository, ApprovalRequestRepository approvalRequestRepository, S3Uploader s3Uploader, S3Presigner s3Presigner) {
        this.attachmentRepository = attachmentRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.s3Uploader = s3Uploader;
        this.s3Presigner = s3Presigner;
    }

    private static final int MAX_ATTACHMENT_COUNT = 3;
    // 첨부파일 업로드
    public List<AttachmentResDto> upload(UUID memberId, UUID requestId,
                                         List<MultipartFile> files) {

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 요청을 찾을 수 없습니다."));

        if (!request.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 결재 요청에만 첨부파일을 업로드할 수 있습니다.");
        }

        if (request.getRequestStatus() != RequestStatus.DRAFT
                && request.getRequestStatus() != RequestStatus.WAIT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "임시저장(DRAFT) 또는 결재대기(WAIT) 상태에서만 첨부파일을 추가할 수 있습니다.");
        }

        // 기존 첨부파일 개수 + 이번 업로드 개수 합산 체크
        long existingCount = attachmentRepository.countByRequestId(requestId);
        if (existingCount + files.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "첨부파일은 최대 " + MAX_ATTACHMENT_COUNT + "개까지 등록할 수 있습니다. "
                            + "(현재 " + existingCount + "개)");
        }

        List<Attachment> savedAttachments = new ArrayList<>();
        for (MultipartFile file : files) {
            String approvalUrl = s3Uploader.upload(file, "approval");

            Attachment attachment = Attachment.builder()
                    .request(request)
                    .fileName(file.getOriginalFilename())
                    .approvalUrl(approvalUrl)
                    .fileSize(file.getSize())
                    .build();

            savedAttachments.add(attachmentRepository.save(attachment));
        }

        return savedAttachments.stream()
                .map(a -> {
                    AttachmentResDto dto = AttachmentResDto.fromEntity(a);
                    dto.setApprovalUrl(generatePresignedUrl(a.getApprovalUrl()));
                    return dto;
                })
                .toList();
    }

    // 첨부파일 목록 조회
    @Transactional(readOnly = true)
    public List<AttachmentResDto> findByRequestId(UUID companyId, UUID memberId, UUID requestId) {

        ApprovalRequest request = approvalRequestRepository.findByIdWithDocument(requestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "결재 요청을 찾을 수 없습니다."));

        if (!request.getApprovalDocument().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        List<Attachment> attachments = attachmentRepository.findByRequestId(requestId);

        return attachments.stream()
                .map(a -> {
                    AttachmentResDto dto = AttachmentResDto.fromEntity(a);
                    dto.setApprovalUrl(generatePresignedUrl(a.getApprovalUrl()));
                    return dto;
                })
                .toList();
    }

    // 첨부파일 삭제
    public void delete(UUID memberId, UUID attachmentId) {

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "첨부파일을 찾을 수 없습니다."));

        if (!attachment.getRequest().getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 첨부파일만 삭제할 수 있습니다.");
        }

        if (attachment.getRequest().getRequestStatus() != RequestStatus.DRAFT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "임시저장(DRAFT) 상태에서만 첨부파일을 삭제할 수 있습니다.");
        }

        s3Uploader.delete(attachment.getApprovalUrl());
        attachmentRepository.delete(attachment);
    }

//    해당 결재요청의 첨부파일 전체 삭제 -> ApprovalRequestService.update에서 호출
    public void deleteAllByRequestId(UUID requestId) {
        List<Attachment> attachments = attachmentRepository.findByRequestId(requestId);

        for (Attachment attachment : attachments) {
            s3Uploader.delete(attachment.getApprovalUrl());
        }

        attachmentRepository.deleteAll(attachments);
    }

    private String generatePresignedUrl(String fileUrl) {
        String fileName = fileUrl.split("amazonaws.com/")[1];
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket("workforce-approval")
                .key(fileName)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }


}
