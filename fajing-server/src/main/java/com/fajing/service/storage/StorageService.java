package com.fajing.service.storage;

/**
 * 图片存储抽象：本地磁盘 / S3 兼容对象存储可切换
 */
public interface StorageService {

    /**
     * 写入一张图片并返回可访问 URL
     *
     * @param bytes  图片字节
     * @param ext    扩展名（jpg/png/webp）
     * @param prefix 文件名前缀（不含扩展名），为空则随机生成
     */
    String put(byte[] bytes, String ext, String prefix);

    /** 按 URL 删除文件，找不到时静默忽略 */
    void delete(String url);
}
