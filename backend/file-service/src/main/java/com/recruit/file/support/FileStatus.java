package com.recruit.file.support;

/**
 * file_meta.status 取值。
 */
public final class FileStatus {

    /** 预签名已签发，对象尚未校验通过。 */
    public static final String UPLOADING = "UPLOADING";

    /** 上传完成且 MIME/大小/校验和通过，可以下载。 */
    public static final String OK = "OK";

    /** 校验失败或被业务拒绝。 */
    public static final String REJECTED = "REJECTED";

    /** 逻辑删除，不返回给调用方。 */
    public static final String DELETED = "DELETED";

    private FileStatus() {
    }
}
