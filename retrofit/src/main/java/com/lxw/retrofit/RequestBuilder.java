package com.lxw.retrofit;

import android.support.annotation.Nullable;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/23
 *     desc   :
 * </pre>
 */
public class RequestBuilder {
    private static final char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F'
    };
//    private static final String PATH_SEGMENT_ALWAYS_ENCODE_SET = " \"<>^`{}|\\?#";
    private static final String PATH_SEGMENT_ALWAYS_ENCODE_SET = " \"<>^`{}|\\?#";
    private final String method;
    private final HttpUrl baseUrl;
    private @Nullable
    String relativeUrl;
    private @Nullable
    HttpUrl.Builder urlBuilder;

    private final Request.Builder requestBuilder;
    private @Nullable
    MediaType contentType;

    private final boolean hasBody;
    private @Nullable
    MultipartBody.Builder multipartBuilder;
    private @Nullable
    FormBody.Builder formBuilder;
    private @Nullable
    RequestBody body;


    public RequestBuilder(String method, HttpUrl baseUrl,
                          String relativeUrl,
                          MediaType contentType,
                          Headers headers,
                          boolean hasBody,
                          boolean isFormEncoded,
                          boolean isMultipart) {
        this.method = method;
        this.baseUrl = baseUrl;
        this.relativeUrl = relativeUrl;
        this.requestBuilder = new Request.Builder();
        this.contentType = contentType;
        this.hasBody = hasBody;

        if (headers != null) {
            requestBuilder.headers(headers);
        }
        if (isFormEncoded) {
            formBuilder = new FormBody.Builder();
        } else if (isMultipart) {
            multipartBuilder = new MultipartBody.Builder();
            multipartBuilder.setType(MultipartBody.FORM);
        }
    }

    public void setRelativeUrl(Object relativeUrl) {
        this.relativeUrl = relativeUrl.toString();
    }

    public void addPathParam(String name, String value, boolean encoded) {
        if (relativeUrl == null) {
            throw new AssertionError();
        }
        relativeUrl = relativeUrl.replace("{" + name + "}", canonicalizeForPath(value, encoded));
    }

    private static String canonicalizeForPath(String input, boolean alreadyEncoded) {
        int codePoint;
        for (int i = 0, limit = input.length(); i < limit; i += Character.charCount(codePoint)) {
            codePoint = input.codePointAt(i);
            if (codePoint < 0x20 || codePoint >= 0x7f
                    || PATH_SEGMENT_ALWAYS_ENCODE_SET.indexOf(codePoint) != -1
                    || (!alreadyEncoded && (codePoint == '/' || codePoint == '%'))) {
                // Slow path: the character at i requires encoding!
                Buffer out = new Buffer();
                out.writeUtf8(input, 0, i);
                canonicalizeForPath(out, input, i, limit, alreadyEncoded);
                return out.readUtf8();
            }
        }
        return input;
    }

    private static void canonicalizeForPath(Buffer out, String input, int pos, int limit,
                                            boolean alreadyEncoded) {
        Buffer utf8Buffer = null;
        // Lazily allocated.
        int codePoint;
        for (int i = pos; i < limit; i += Character.charCount(codePoint)) {
            codePoint = input.codePointAt(i);
            if (alreadyEncoded
                    && (codePoint == '\t' || codePoint == '\n' || codePoint == '\f' || codePoint == '\r')) {
                // Skip this character.
            } else if (codePoint < 0x20 || codePoint >= 0x7f
                    || PATH_SEGMENT_ALWAYS_ENCODE_SET.indexOf(codePoint) != -1
                    || (!alreadyEncoded && (codePoint == '/' || codePoint == '%'))) {
                // Percent encode this character.
                if (utf8Buffer == null) {
                    utf8Buffer = new Buffer();
                }
                utf8Buffer.writeUtf8CodePoint(codePoint);
                while (!utf8Buffer.exhausted()) {
                    int b = utf8Buffer.readByte() & 0xff;
                    out.writeByte('%');
                    out.writeByte(HEX_DIGITS[(b >> 4) & 0xf]);
                    out.writeByte(HEX_DIGITS[b & 0xf]);
                }
            } else {
                // This character doesn't need encoding. Just copy it over.
                out.writeUtf8CodePoint(codePoint);
            }
        }
    }

    public void addQueryParam(String name, String queryValue, boolean encoded) {
        if (relativeUrl != null) {
            urlBuilder = baseUrl.newBuilder(relativeUrl);
            if (urlBuilder == null) {
                throw new IllegalArgumentException(
                        "Malformed URL. Base: " + baseUrl + ", Relative: " + relativeUrl);
            }
            relativeUrl = null;
        }

        if (encoded) {
            urlBuilder.addEncodedQueryParameter(name, queryValue);
        } else {
            urlBuilder.addQueryParameter(name, queryValue);
        }

    }

    public void addHeader(String name, String value) {
        if ("Content-Type".equalsIgnoreCase(name)) {
            MediaType type = MediaType.parse(value);
            if (type == null) {
                throw new IllegalArgumentException("Malformed content type: " + value);
            }
            contentType = type;
        } else {
            requestBuilder.addHeader(name, value);
        }
    }

    public void addFormField(String name, String value, boolean encoded) {
        if (encoded) {
            formBuilder.addEncoded(name, value);
        } else {
            formBuilder.add(name, value);
        }
    }

    public void addPart(MultipartBody.Part part) {
        multipartBuilder.addPart(part);
    }

    // Only called when isMultipart was true.
    void addPart(Headers headers, RequestBody body) {
        multipartBuilder.addPart(headers, body);
    }

    void setBody(RequestBody body) {
        this.body = body;
    }

    public Request build() {
        HttpUrl url;
        HttpUrl.Builder urlBuilder = this.urlBuilder;
        if (urlBuilder != null) {
            url = urlBuilder.build();
        } else {
            url = baseUrl.resolve(relativeUrl);
            if (url == null) {
                throw new IllegalArgumentException(
                        "Malformed URL. Base: " + baseUrl + ", Relative: " + relativeUrl);
            }
        }
        RequestBody body = this.body;
        if (body == null) {
            if (formBuilder != null) {
                body = formBuilder.build();
            } else if (multipartBuilder != null) {
                body = multipartBuilder.build();
            } else if (hasBody) {
                body = RequestBody.create(contentType, new byte[0]);
            }
        }

        MediaType contentType = this.contentType;
        if (contentType != null) {
            if (body != null) {
                //没看懂 为什么要继承写一个
                body = new ContentTypeOverringRequestBody(body, contentType);
            } else {
                requestBuilder.addHeader("Content-Type", contentType.toString());
            }
        }

        return requestBuilder.url(url).method(method, body).build();
    }

    private static class ContentTypeOverringRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final MediaType contentType;

        public ContentTypeOverringRequestBody(RequestBody delegate, MediaType contentType) {
            this.delegate = delegate;
            this.contentType = contentType;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            delegate.writeTo(sink);
        }
    }
}
