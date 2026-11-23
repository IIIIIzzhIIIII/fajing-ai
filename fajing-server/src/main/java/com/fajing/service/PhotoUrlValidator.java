package com.fajing.service;

import com.fajing.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * 用户照片地址校验：只允许本站 /uploads、相对路径或已配置的 S3 公网域名，
 * 防止把任意外部地址传给服务端拉取（SSRF）或误把小程序临时路径当远程 URL。
 */
@Service
public class PhotoUrlValidator {

    @Value("${fajing.base-url:}")
    private String baseUrl;

    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    /** 校验并返回去除首尾空白的地址，不合法直接抛业务异常 */
    public String check(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("请先上传照片");
        }
        String url = photoUrl.trim();
        if (url.startsWith("http://tmp/") || url.startsWith("http://usr/")
                || url.startsWith("wxfile://")) {
            throw new BizException("照片未上传成功，请重新选择照片");
        }
        if (url.startsWith("/uploads/")) {
            return url;
        }
        URI target = parse(url);
        if (target == null) {
            throw new BizException("照片地址不合法");
        }
        String scheme = target.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BizException("照片地址不合法");
        }
        URI base = parse(baseUrl);
        if (base != null && sameOrigin(target, base)
                && target.getPath() != null && target.getPath().startsWith("/uploads/")) {
            return url;
        }
        URI s3 = parse(s3PublicBaseUrl);
        if (s3 != null && sameOrigin(target, s3)) {
            return url;
        }
        throw new BizException("照片地址不合法");
    }

    private static URI parse(String url) {
        try {
            return url == null || url.isBlank() ? null : URI.create(url.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme() != null && b.getScheme() != null
                && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && b.getHost() != null
                && a.getHost().equalsIgnoreCase(b.getHost())
                && portOf(a) == portOf(b);
    }

    private static int portOf(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
