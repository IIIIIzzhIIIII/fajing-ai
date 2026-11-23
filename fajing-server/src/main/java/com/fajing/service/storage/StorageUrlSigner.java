package com.fajing.service.storage;

import java.time.Duration;

/**
 * 对象存储 URL 签名：私有桶通过预签名地址提供短时访问。
 * 本地存储不实现该接口，继续使用 /uploads 的 HMAC 签名。
 */
public interface StorageUrlSigner {

    /** 返回带有效期的预签名 URL；失败时可返回原 URL */
    String presign(String url, Duration ttl);
}
