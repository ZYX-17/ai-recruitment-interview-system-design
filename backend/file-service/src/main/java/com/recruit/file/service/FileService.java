package com.recruit.file.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.file.config.MinioProperties;
import com.recruit.file.dto.CompleteUploadRequest;
import com.recruit.file.dto.DownloadUrlResponse;
import com.recruit.file.dto.FileMetaVO;
import com.recruit.file.dto.InternalRegisterRequest;
import com.recruit.file.dto.PresignUploadRequest;
import com.recruit.file.dto.PresignUploadResponse;
import com.recruit.file.entity.FileMeta;
import com.recruit.file.mapper.FileMetaMapper;
import com.recruit.file.support.FilePolicy;
import com.recruit.file.support.FileStatus;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务核心：预签名上传、完成校验、下载、内部登记。
 *
 * <p>上传状态机：{@code UPLOADING → OK / REJECTED / DELETED}。完成接口以 MinIO
 * 中对象的真实大小为准，并尽量计算 SHA-256；大小或校验和不符直接拒绝，
 * 防止“预签名声明 PDF、实际传别的内容”。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 超过该大小的对象默认不做全量 SHA-256（录像等大文件），除非调用方显式提供摘要。 */
    private static final long HASH_LIMIT_BYTES = 100L * 1024L * 1024L;

    private static final DateTimeFormatter OBJECT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileMetaMapper fileMetaMapper;
    private final MinioClient minioClient;
    private final MinioProperties properties;

    /**
     * 获取预签名上传 URL；若同一上传人已上传过相同内容（SHA-256 相同），直接秒传。
     */
    public PresignUploadResponse presignUpload(PresignUploadRequest request, long uploaderId) {
        String fileName = sanitizeFileName(request.fileName());
        String mime = FilePolicy.normalizeMime(request.mime());
        String bizType = normalizeBizType(request.bizType());
        FilePolicy.validate(bizType, mime, request.size());
        String sha256 = normalizeSha256(request.sha256());

        if (!sha256.isEmpty()) {
            FileMeta reusable = findReusable(sha256, uploaderId, bizType);
            if (reusable != null) {
                return new PresignUploadResponse(String.valueOf(reusable.getId()), reusable.getFileKey(),
                        null, "PUT", Map.of(), null, true);
            }
        }

        String fileKey = newFileKey();
        String objectKey = buildObjectKey(bizType, fileKey, fileName, mime);

        FileMeta meta = new FileMeta();
        meta.setFileKey(fileKey);
        meta.setBucket(properties.getBucket());
        meta.setObjectKey(objectKey);
        meta.setSha256(sha256);
        meta.setMime(mime);
        meta.setSize(request.size());
        meta.setBizType(bizType);
        meta.setBizId(request.bizId());
        meta.setFileName(fileName);
        meta.setUploaderId(uploaderId);
        meta.setStatus(FileStatus.UPLOADING);
        fileMetaMapper.insert(meta);

        try {
            String url = presign(meta, Method.PUT);
            return new PresignUploadResponse(String.valueOf(meta.getId()), fileKey, url, "PUT",
                    Map.of("Content-Type", mime), expireAt(), false);
        } catch (RuntimeException e) {
            // 预签名失败时回滚登记，避免留下永远无法完成的 UPLOADING 记录
            fileMetaMapper.deleteById(meta.getId());
            throw e;
        }
    }

    /**
     * 用户侧完成上传：校验对象存在、大小、MIME、SHA-256，然后置为 OK。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetaVO complete(CompleteUploadRequest request, Long operatorId) {
        FileMeta meta = requireByFileKey(request.fileKey());
        if (!meta.getBizType().equalsIgnoreCase(request.bizType().trim())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 与预签名登记不一致");
        }
        return doComplete(meta, request.fileName(), request.mime(), request.size(), request.sha256(), operatorId);
    }

    /**
     * 服务间完成上传：文件 ID 在路径上，MIME/大小/文件名以登记值为准，只校验可选 sha256。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetaVO completeInternal(long fileId, String sha256) {
        FileMeta meta = requireById(fileId);
        return doComplete(meta, meta.getFileName(), meta.getMime(), null, sha256, null);
    }

    public FileMetaVO getMeta(long id) {
        return FileMetaVO.from(requireById(id));
    }

    public FileMetaVO getMetaByFileKey(String fileKey) {
        return FileMetaVO.from(requireByFileKey(fileKey));
    }

    /**
     * 生成下载预签名 URL；只有 OK 状态的文件允许下载。
     */
    public DownloadUrlResponse downloadUrl(long id) {
        FileMeta meta = requireById(id);
        if (!FileStatus.OK.equals(meta.getStatus())) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "文件尚未完成上传");
        }
        String url = presign(meta, Method.GET);
        return new DownloadUrlResponse(String.valueOf(meta.getId()), url, expireAt());
    }

    /**
     * 服务间登记：对象已经由调用方写入 MinIO，这里只落元数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileMetaVO register(InternalRegisterRequest request) {
        String bizType = normalizeBizType(request.bizType());
        String mime = FilePolicy.normalizeMime(request.mime());
        FilePolicy.validate(bizType, mime, request.size());

        if (StringUtils.hasText(request.fileKey())) {
            FileMeta byKey = findByFileKey(request.fileKey().trim());
            if (byKey != null) {
                return FileMetaVO.from(byKey);
            }
        }
        FileMeta byObject = fileMetaMapper.selectOne(Wrappers.<FileMeta>lambdaQuery()
                .eq(FileMeta::getObjectKey, request.objectKey())
                .last("LIMIT 1"));
        if (byObject != null) {
            return FileMetaVO.from(byObject);
        }

        FileMeta meta = new FileMeta();
        meta.setFileKey(StringUtils.hasText(request.fileKey()) ? request.fileKey().trim() : newFileKey());
        meta.setBucket(StringUtils.hasText(request.bucket()) ? request.bucket().trim() : properties.getBucket());
        meta.setObjectKey(request.objectKey().trim());
        meta.setSha256(normalizeSha256(request.sha256()));
        meta.setMime(mime);
        meta.setSize(request.size());
        meta.setBizType(bizType);
        meta.setBizId(request.bizId());
        meta.setFileName(sanitizeFileName(request.fileName()));
        meta.setUploaderId(request.uploaderId());
        meta.setStatus(FileStatus.OK);
        fileMetaMapper.insert(meta);
        return FileMetaVO.from(meta);
    }

    private FileMetaVO doComplete(FileMeta meta, String fileName, String mime,
                                  Long declaredSize, String declaredSha256, Long operatorId) {
        if (operatorId != null && !operatorId.equals(meta.getUploaderId())) {
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FORBIDDEN, "无权操作该文件");
        }
        // 幂等：重复 complete 直接返回已完成结果
        if (FileStatus.OK.equals(meta.getStatus())) {
            return FileMetaVO.from(meta);
        }
        if (FileStatus.DELETED.equals(meta.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        if (FileStatus.REJECTED.equals(meta.getStatus())) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE,
                    StringUtils.hasText(meta.getRejectReason()) ? meta.getRejectReason() : "文件已被拒绝");
        }

        String normalizedMime = FilePolicy.normalizeMime(mime);
        if (StringUtils.hasText(meta.getMime()) && !meta.getMime().equalsIgnoreCase(normalizedMime)) {
            throw new BizException(ErrorCode.FILE_MIME_UNSUPPORTED, "MIME 与预签名登记不一致");
        }

        StatObjectResponse stat = statObject(meta);
        if (stat == null || stat.size() <= 0) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "对象不存在或为空，请确认已上传");
        }
        long actualSize = stat.size();
        if (declaredSize != null && declaredSize != actualSize) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "文件大小与声明不一致");
        }
        FilePolicy.validate(meta.getBizType(), normalizedMime, actualSize);

        String declared = normalizeSha256(declaredSha256);
        String known = meta.getSha256() == null ? "" : meta.getSha256();
        String actual = "";
        // 小文件总是计算；大文件只在调用方给出摘要或预签名时已声明摘要时计算
        if (actualSize <= HASH_LIMIT_BYTES || !declared.isEmpty() || !known.isEmpty()) {
            actual = sha256(meta);
        }
        if (!declared.isEmpty() && !actual.equalsIgnoreCase(declared)) {
            throw new BizException(ErrorCode.FILE_CHECKSUM_MISMATCH);
        }
        if (!known.isEmpty() && !actual.isEmpty() && !known.equalsIgnoreCase(actual)) {
            throw new BizException(ErrorCode.FILE_CHECKSUM_MISMATCH, "文件内容与预签名声明的 SHA-256 不一致");
        }

        meta.setFileName(sanitizeFileName(
                StringUtils.hasText(fileName) ? fileName : meta.getFileName()));
        meta.setMime(normalizedMime);
        meta.setSize(actualSize);
        if (!actual.isEmpty()) {
            meta.setSha256(actual);
        }
        meta.setStatus(FileStatus.OK);
        meta.setRejectReason(null);

        int updated = fileMetaMapper.updateById(meta);
        if (updated == 0) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "文件状态已变更，请重试");
        }
        return FileMetaVO.from(meta);
    }

    private FileMeta requireById(long id) {
        FileMeta meta = fileMetaMapper.selectById(id);
        if (meta == null || FileStatus.DELETED.equals(meta.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return meta;
    }

    private FileMeta requireByFileKey(String fileKey) {
        FileMeta meta = findByFileKey(fileKey);
        if (meta == null || FileStatus.DELETED.equals(meta.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return meta;
    }

    private FileMeta findByFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }
        return fileMetaMapper.selectOne(Wrappers.<FileMeta>lambdaQuery()
                .eq(FileMeta::getFileKey, fileKey.trim())
                .last("LIMIT 1"));
    }

    private FileMeta findReusable(String sha256, long uploaderId, String bizType) {
        return fileMetaMapper.selectOne(Wrappers.<FileMeta>lambdaQuery()
                .eq(FileMeta::getSha256, sha256)
                .eq(FileMeta::getUploaderId, uploaderId)
                .eq(FileMeta::getBizType, bizType)
                .eq(FileMeta::getStatus, FileStatus.OK)
                .orderByDesc(FileMeta::getId)
                .last("LIMIT 1"));
    }

    private String presign(FileMeta meta, Method method) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(meta.getBucket())
                    .object(meta.getObjectKey())
                    .expiry(properties.getPresignTtlMinutes(), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败 fileKey={}, method={}", meta.getFileKey(), method, e);
            ErrorCode code = method == Method.PUT ? ErrorCode.FILE_PRESIGN_FAILED : ErrorCode.FILE_STORAGE_ERROR;
            throw new BizException(code, e);
        }
    }

    private StatObjectResponse statObject(FileMeta meta) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(meta.getBucket())
                    .object(meta.getObjectKey())
                    .build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                return null;
            }
            log.error("读取对象元数据失败 fileKey={}", meta.getFileKey(), e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        } catch (Exception e) {
            log.error("读取对象元数据失败 fileKey={}", meta.getFileKey(), e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    private String sha256(FileMeta meta) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(meta.getBucket())
                .object(meta.getObjectKey())
                .build())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.error("计算文件 SHA-256 失败 fileKey={}", meta.getFileKey(), e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    private String expireAt() {
        return Instant.now().plus(properties.getPresignTtlMinutes(), ChronoUnit.MINUTES).toString();
    }

    private String newFileKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String buildObjectKey(String bizType, String fileKey, String fileName, String mime) {
        String date = LocalDate.now(ZoneOffset.UTC).format(OBJECT_DATE);
        return bizType.toLowerCase(Locale.ROOT) + "/" + date + "/" + fileKey
                + "." + FilePolicy.extensionOf(fileName, mime);
    }

    private String normalizeBizType(String bizType) {
        return bizType == null ? "" : bizType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSha256(String sha256) {
        if (!StringUtils.hasText(sha256)) {
            return "";
        }
        String value = sha256.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-f0-9]{64}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "sha256 必须是 64 位十六进制");
        }
        return value;
    }

    /**
     * 去掉客户端传路径穿越字符后的文件名（只保留最后一段）。
     */
    private String sanitizeFileName(String fileName) {
        String value = fileName == null ? "" : fileName.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件名不能为空");
        }
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }
}
