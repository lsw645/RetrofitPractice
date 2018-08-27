package com.lxw.retrofit;

import com.lxw.retrofit.http.Body;
import com.lxw.retrofit.http.Delete;
import com.lxw.retrofit.http.Field;
import com.lxw.retrofit.http.FieldMap;
import com.lxw.retrofit.http.FormUrlEncoded;
import com.lxw.retrofit.http.GET;
import com.lxw.retrofit.http.HEAD;
import com.lxw.retrofit.http.HTTP;
import com.lxw.retrofit.http.Header;
import com.lxw.retrofit.http.HeaderMap;
import com.lxw.retrofit.http.Multipart;
import com.lxw.retrofit.http.OPTIONS;
import com.lxw.retrofit.http.PATCH;
import com.lxw.retrofit.http.POST;
import com.lxw.retrofit.http.PUT;
import com.lxw.retrofit.http.Part;
import com.lxw.retrofit.http.PartMap;
import com.lxw.retrofit.http.Path;
import com.lxw.retrofit.http.Query;
import com.lxw.retrofit.http.QueryMap;
import com.lxw.retrofit.http.QueryName;
import com.lxw.retrofit.http.Url;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/23
 *     desc   :
 * </pre>
 */
public class ServiceMethod<R, T> {
    private static final String PARAM = "[a-zA-Z][a-zA-Z0-9_-]*";
    private static final Pattern PARAM_URL_REGEX = Pattern.compile("\\{(" + PARAM + ")\\}");
    private static final Pattern PARAM_NAME_REGEX = Pattern.compile(PARAM);

    private final okhttp3.Call.Factory callFactory;
    private final CallAdapter<R, T> callAdapter;
    private final HttpUrl baseUrl;
    private Converter<ResponseBody, R> responseConverter;
    private final String httpMethod;
    private final String relativeUrl;
    private final Headers headers;
    private final MediaType contentType;
    private final boolean isFormEncoded;
    private final boolean isMultipart;
    private final ParameterHandler<?>[] parameterHandlers;
    private final boolean hasBody;


    public ServiceMethod(Builder<R, T> builder) {
        this.callFactory = builder.retrofit.callFactory();
        this.callAdapter = builder.callAdapter;
        this.baseUrl = builder.retrofit.baseUrl();
        this.responseConverter = builder.responseConverter;
        this.httpMethod = builder.httpMethod;
        this.relativeUrl = builder.relativeUrl;
        this.headers = builder.headers;
        this.contentType = builder.contentType;
        this.hasBody = builder.hasBody;
        this.isFormEncoded = builder.isFormEncoded;
        this.isMultipart = builder.isMultipart;
        this.parameterHandlers = builder.parameterHandlers;
    }

    public T adapt(Call<R> okHttpCall) {
        return callAdapter.adapt(okHttpCall);
    }

    public okhttp3.Call toCall(Object[] args) throws IOException {
        RequestBuilder requestBuilder = new RequestBuilder(
                httpMethod,
                baseUrl, relativeUrl, contentType, headers, hasBody,
                isFormEncoded, isMultipart
        );
        int argumentCount = args.length;
        ParameterHandler<Object>[] handlers = (ParameterHandler<Object>[]) parameterHandlers;
        for (int p = 0; p < argumentCount; p++) {
            handlers[p].apply(requestBuilder, args[p]);
        }

        return callFactory.newCall(requestBuilder.build());
    }

    R toResponse(ResponseBody body) throws IOException {
        return responseConverter.convert(body);
    }

    public static final class Builder<T, R> {
        final Retrofit retrofit;
        final Method method;
        final Annotation[] methodAnnotations;
        final Annotation[][] parameterAnnotationArray;
        final Type[] parameterTypes;
        //返回类型
        Type responseType;
        boolean gotField;
        boolean gotPart;
        boolean gotBody;
        boolean gotPath;
        boolean gotQuery;
        boolean gotUrl;

        String httpMethod;
        boolean hasBody;
        boolean isFormEncoded;
        boolean isMultipart;
        String relativeUrl;
        Headers headers;
        MediaType contentType;
        Set<String> relativeUrlParamNames;
        Converter<ResponseBody, T> responseConverter;
        CallAdapter<T, R> callAdapter;
        ParameterHandler[] parameterHandlers;

        public Builder(Retrofit retrofit, Method method) {
            this.retrofit = retrofit;
            this.method = method;
            methodAnnotations = method.getAnnotations();
            parameterAnnotationArray = method.getParameterAnnotations();
            parameterTypes = method.getParameterTypes();
        }

        public ServiceMethod build() {
            callAdapter = createCallAdapter();
            responseType = callAdapter.responseType();
            if (responseType == Response.class || responseType == okhttp3.Response.class) {
                throw methodError("'"
                        + Utils.getRawType(responseType).getName()
                        + "' is not a valid response body type. Did you mean ResponseBody?");
            }
            //TODO 获得Response转换器  BufferingResponseBodyConverter
            responseConverter = createResponseConverter();
            //解析方法上的 注解
            for (Annotation methodAnnotation : methodAnnotations) {
                parseMethodAnnnoatation(methodAnnotation);
            }

            if (httpMethod == null) {
                throw methodError("HTTP method annotation is required (e.g., @GET, @POST, etc.).");
            }

            if (!hasBody) {
                if (isMultipart) {
                    throw methodError(
                            "Multipart can only be specified on HTTP methods with request body (e" +
                                    ".g., @POST).");
                }
                if (isFormEncoded) {
                    throw methodError("FormUrlEncoded can only be specified on HTTP methods with "
                            + "request body (e.g., @POST).");
                }
            }
            //多少个参数
            int parameterCount = parameterAnnotationArray.length;
            parameterHandlers = new ParameterHandler[parameterCount];

            for (int p = 0; p < parameterCount; p++) {
                //获得方法参数类型
                Type parameterType = parameterTypes[p];
                if (Utils.hasUnresolvableType(parameterType)) {
                    throw parameterError(p, "Parameter type must not include a type variable or " +
                                    "wildcard: %s",
                            parameterType);
                }
                ;
                Annotation[] parameterAnnotations = parameterAnnotationArray[p];
                if (parameterAnnotations == null) {
                    throw parameterError(p, "No Retrofit annotation found.");
                }
                //解析参数上的注解
                parameterHandlers[p] = parseParameter(p, parameterAnnotations, parameterType);
            }
            if (relativeUrl == null && !gotUrl) {
                throw methodError("Missing either @%s URL or @Url parameter.", httpMethod);
            }
            if (!isFormEncoded && !isMultipart && !hasBody && gotBody) {
                throw methodError("Non-body HTTP method cannot contain @Body.");
            }
            if (isFormEncoded && !gotField) {
                throw methodError("Form-encoded method must contain at least one @Field.");
            }
            if (isMultipart && !gotPart) {
                throw methodError("Multipart method must contain at least one @Part.");
            }

            return new ServiceMethod<>(this);
        }

        private ParameterHandler parseParameter(int p, Annotation[] annotations, Type parameterType) {
            //判断 参数是否有且只有一个注解
            ParameterHandler<?> result = null;
            //虽然可以给参数多个注解，但这里强制只允许一个
            for (Annotation annotation : annotations) {
                ParameterHandler<?> annotationAction = parseParameterAnnotation(p, parameterType, annotations, annotation);
                if (annotationAction == null) {
                    continue;
                }

                if (result != null) {
                    throw parameterError(p, "Multiple Retrofit annotations found, only one " +
                            "allowed.");
                }
                result = annotationAction;
            }
            if (result == null) {
                throw parameterError(p, "No Retrofit annotation found.");
            }

            return result;
        }

        private ParameterHandler<?> parseParameterAnnotation(int p,
                                                             Type parameterType,
                                                             Annotation[] annotations,
                                                             Annotation annotation) {
            if (annotation instanceof Url) {
                if (gotUrl) {
                    throw parameterError(p, "Multiple @Url method annotations found.");
                }
                if (gotPath) {
                    throw parameterError(p, "@Path parameters may not be used with @Url.");
                }

                if (gotQuery) {
                    throw parameterError(p, "A @Url parameter must not come after a @Query");
                }

                if (relativeUrl != null) {
                    throw parameterError(p, "@Url cannot be used with @%s URL", httpMethod);
                }
                gotUrl = true;
                if (parameterType == HttpUrl.class
                        || parameterType == String.class
                        || parameterType == URI.class
                        || (parameterType instanceof Class && "android.net.Uri".equals(((Class<?>) parameterType)
                        .getName()))) {
                    return new ParameterHandler.RelativeUrl();
                } else {
                    throw parameterError(p,
                            "@Url must be okhttp3.HttpUrl, String, java.net.URI, or android.net" +
                                    ".Uri type.");
                }
            } else if (annotation instanceof Path) {
                if (gotQuery) {
                    throw parameterError(p, "A @Path parameter must not come after a @Query.");
                }
                if (gotUrl) {
                    throw parameterError(p, "@Path parameters may not be used with @Url.");
                }
                if (relativeUrl == null) {
                    throw parameterError(p, "@Path can only be used with relative url on @%s",
                            httpMethod);
                }
                gotPath = true;
                Path path = (Path) annotation;
                String name = path.value();
                validatePathName(p, name);

                Converter<Object, String> converter = retrofit.stringConverter(parameterType, annotations);
                return new ParameterHandler.Path<>(name, converter, path.encoded());
            } else if (annotation instanceof Query) {
                Query query = (Query) annotation;
                String name = query.value();
                boolean encoded = query.encoded();
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                gotQuery = true;
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(parameterType instanceof ParameterizedType)) {
                        throw parameterError(p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded);
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType).getComponentType();
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(parameterType, annotations);
                    return new ParameterHandler.Query<>(name, converter, encoded);
                }

            } else if (annotation instanceof QueryName) {
                QueryName query = (QueryName) annotation;
                boolean encoded = query.encoded();
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                gotQuery = true;
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(parameterType instanceof ParameterizedType)) {
                        throw parameterError(p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType
                            .getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(parameterType, annotations);
                    return new ParameterHandler.QueryName<>(converter, encoded);
                }
            } else if (annotation instanceof QueryMap) {
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(p, "@QueryMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(parameterType, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(p, "Map must include generic types (e.g., Map<String, " +
                            "String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(p, "@QueryMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                return new ParameterHandler.QueryMap<>(valueConverter, ((QueryMap) annotation)
                        .encoded());

            } else if (annotation instanceof Header) {
                Header header = (Header) annotation;
                String name = header.value();

                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(parameterType instanceof ParameterizedType)) {
                        throw parameterError(p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Header<>(name, converter).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType
                            .getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Header<>(name, converter).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(parameterType, annotations);
                    return new ParameterHandler.Header<>(name, converter);
                }

            } else if (annotation instanceof HeaderMap) {
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(p, "@HeaderMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(parameterType, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(p, "Map must include generic types (e.g., Map<String, " +
                            "String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(p, "@HeaderMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                return new ParameterHandler.HeaderMap<>(valueConverter);

            } else if (annotation instanceof Field) {
                if (!isFormEncoded) {
                    throw parameterError(p, "@Field parameters can only be used with form " +
                            "encoding.");
                }
                Field field = (Field) annotation;
                String name = field.value();
                boolean encoded = field.encoded();

                gotField = true;

                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (Iterable.class.isAssignableFrom(rawParameterType)) {
                    if (!(parameterType instanceof ParameterizedType)) {
                        throw parameterError(p, rawParameterType.getSimpleName()
                                + " must include generic type (e.g., "
                                + rawParameterType.getSimpleName()
                                + "<String>)");
                    }
                    ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                    Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                    Converter<?, String> converter =
                            retrofit.stringConverter(iterableType, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded).iterable();
                } else if (rawParameterType.isArray()) {
                    Class<?> arrayComponentType = boxIfPrimitive(rawParameterType
                            .getComponentType());
                    Converter<?, String> converter =
                            retrofit.stringConverter(arrayComponentType, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded).array();
                } else {
                    Converter<?, String> converter =
                            retrofit.stringConverter(parameterType, annotations);
                    return new ParameterHandler.Field<>(name, converter, encoded);
                }

            } else if (annotation instanceof FieldMap) {
                if (!isFormEncoded) {
                    throw parameterError(p, "@FieldMap parameters can only be used with form " +
                            "encoding.");
                }
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(p, "@FieldMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(parameterType, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(p,
                            "Map must include generic types (e.g., Map<String, String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;
                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(p, "@FieldMap keys must be of type String: " + keyType);
                }
                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                Converter<?, String> valueConverter =
                        retrofit.stringConverter(valueType, annotations);

                gotField = true;
                return new ParameterHandler.FieldMap<>(valueConverter, ((FieldMap) annotation)
                        .encoded());

            } else if (annotation instanceof Part) {
                if (!isMultipart) {
                    throw parameterError(p, "@Part parameters can only be used with multipart " +
                            "encoding.");
                }
                Part part = (Part) annotation;
                gotPart = true;

                String partName = part.value();
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (partName.isEmpty()) {
                    //如果是 Iterable或者Array 的话 ，使用MultiPartBody来构建上传
                    if (Iterable.class.isAssignableFrom(rawParameterType)) {
                        if (!(parameterType instanceof ParameterizedType)) {
                            throw parameterError(p, rawParameterType.getSimpleName()
                                    + " must include generic type (e.g., "
                                    + rawParameterType.getSimpleName()
                                    + "<String>)");
                        }
                        ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                        Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                        if (!MultipartBody.Part.class.isAssignableFrom(Utils.getRawType
                                (iterableType))) {
                            throw parameterError(p,
                                    "@Part annotation must supply a name or use MultipartBody" +
                                            ".Part parameter type.");
                        }
                        return ParameterHandler.RawPart.INSTANCE.iterable();
                    } else if (rawParameterType.isArray()) {
                        Class<?> arrayComponentType = rawParameterType.getComponentType();
                        if (!MultipartBody.Part.class.isAssignableFrom(arrayComponentType)) {
                            throw parameterError(p,
                                    "@Part annotation must supply a name or use MultipartBody" +
                                            ".Part parameter type.");
                        }
                        return ParameterHandler.RawPart.INSTANCE.array();
                    } else if (MultipartBody.Part.class.isAssignableFrom(rawParameterType)) {
                        return ParameterHandler.RawPart.INSTANCE;
                    } else {
                        throw parameterError(p,
                                "@Part annotation must supply a name or use MultipartBody.Part " +
                                        "parameter type.");
                    }
                } else {
                    okhttp3.Headers headers =
                            okhttp3.Headers.of("Content-Disposition", "form-data; name=\"" + partName +
                                            "\"",
                                    "Content-Transfer-Encoding", part.encoding());

                    if (Iterable.class.isAssignableFrom(rawParameterType)) {
                        if (!(parameterType instanceof ParameterizedType)) {
                            throw parameterError(p, rawParameterType.getSimpleName()
                                    + " must include generic type (e.g., "
                                    + rawParameterType.getSimpleName()
                                    + "<String>)");
                        }
                        ParameterizedType parameterizedType = (ParameterizedType) parameterType;
                        Type iterableType = Utils.getParameterUpperBound(0, parameterizedType);
                        if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType
                                (iterableType))) {
                            throw parameterError(p, "@Part parameters using the MultipartBody" +
                                    ".Part must not "
                                    + "include a part name in the annotation.");
                        }
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(iterableType, annotations,
                                        methodAnnotations);
                        return new ParameterHandler.Part<>(headers, converter).iterable();
                    } else if (rawParameterType.isArray()) {
                        Class<?> arrayComponentType = boxIfPrimitive(rawParameterType
                                .getComponentType());
                        if (MultipartBody.Part.class.isAssignableFrom(arrayComponentType)) {
                            throw parameterError(p, "@Part parameters using the MultipartBody" +
                                    ".Part must not "
                                    + "include a part name in the annotation.");
                        }
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(arrayComponentType, annotations,
                                        methodAnnotations);
                        return new ParameterHandler.Part<>(headers, converter).array();
                    } else if (MultipartBody.Part.class.isAssignableFrom(rawParameterType)) {
                        throw parameterError(p, "@Part parameters using the MultipartBody.Part " +
                                "must not "
                                + "include a part name in the annotation.");
                    } else {
                        Converter<?, RequestBody> converter =
                                retrofit.requestBodyConverter(parameterType, annotations, methodAnnotations);
                        return new ParameterHandler.Part<>(headers, converter);
                    }
                }

            } else if (annotation instanceof PartMap) {
                if (!isMultipart) {
                    throw parameterError(p, "@PartMap parameters can only be used with multipart " +
                            "encoding.");
                }
                gotPart = true;
                Class<?> rawParameterType = Utils.getRawType(parameterType);
                if (!Map.class.isAssignableFrom(rawParameterType)) {
                    throw parameterError(p, "@PartMap parameter type must be Map.");
                }
                Type mapType = Utils.getSupertype(parameterType, rawParameterType, Map.class);
                if (!(mapType instanceof ParameterizedType)) {
                    throw parameterError(p, "Map must include generic types (e.g., Map<String, " +
                            "String>)");
                }
                ParameterizedType parameterizedType = (ParameterizedType) mapType;

                Type keyType = Utils.getParameterUpperBound(0, parameterizedType);
                if (String.class != keyType) {
                    throw parameterError(p, "@PartMap keys must be of type String: " + keyType);
                }

                Type valueType = Utils.getParameterUpperBound(1, parameterizedType);
                if (MultipartBody.Part.class.isAssignableFrom(Utils.getRawType(valueType))) {
                    throw parameterError(p, "@PartMap values cannot be MultipartBody.Part. "
                            + "Use @Part List<Part> or a different value type instead.");
                }

                Converter<?, RequestBody> valueConverter =
                        retrofit.requestBodyConverter(valueType, annotations, methodAnnotations);

                PartMap partMap = (PartMap) annotation;
                return new ParameterHandler.PartMap<>(valueConverter, partMap.encoding());
            //如果使用默认的requestBodyConverter  只能使用requestBody上传对象
            } else if (annotation instanceof Body) {
                if (isFormEncoded || isMultipart) {
                    throw parameterError(p,
                            "@Body parameters cannot be used with form or multi-part encoding.");
                }
                if (gotBody) {
                    throw parameterError(p, "Multiple @Body method annotations found.");
                }

                Converter<?, RequestBody> converter;
                try {
                    converter = retrofit.requestBodyConverter(parameterType, annotations, methodAnnotations);
                } catch (RuntimeException e) {
                    // Wide exception range because factories are user code.
                    throw parameterError(e, p, "Unable to create @Body converter for %s", parameterTypes);
                }
                gotBody = true;
                return new ParameterHandler.Body<>(converter);
            }

            return null; // Not a Retrofit annotation.

        }

        private void validatePathName(int p, String name) {
            if (!PARAM_NAME_REGEX.matcher(name).matches()) {
                throw parameterError(p, "@Path parameter name must match %s. Found: %s",
                        PARAM_URL_REGEX.pattern(), name);
            }
            // Verify URL replacement name is actually present in the URL path.
            if (!relativeUrlParamNames.contains(name)) {
                throw parameterError(p, "URL \"%s\" does not contain \"{%s}\".", relativeUrl, name);
            }
        }

        private void parseMethodAnnnoatation(Annotation annotation) {
            if (annotation instanceof Delete) {
                parseHttpMethodAndPath("DELETE", ((Delete) annotation).value(), false);
            } else if (annotation instanceof GET) {
                parseHttpMethodAndPath("GET", ((GET) annotation).value(), false);
            } else if (annotation instanceof HEAD) {
                parseHttpMethodAndPath("HEAD", ((HEAD) annotation).value(), false);
                if (!Void.class.equals(responseType)) {
                    throw methodError("HEAD method must use Void as response type.");
                }
            } else if (annotation instanceof PATCH) {
                parseHttpMethodAndPath("PATCH", ((PATCH) annotation).value(), true);
            } else if (annotation instanceof POST) {
                parseHttpMethodAndPath("POST", ((POST) annotation).value(), true);
            } else if (annotation instanceof PUT) {
                parseHttpMethodAndPath("PUT", ((PUT) annotation).value(), true);
            } else if (annotation instanceof OPTIONS) {
                parseHttpMethodAndPath("OPTIONS", ((OPTIONS) annotation).value(), false);
            } else if (annotation instanceof HTTP) {
                HTTP http = (HTTP) annotation;
                parseHttpMethodAndPath(http.method(), http.path(), http.hasBody());
            } else if (annotation instanceof com.lxw.retrofit.http.Headers) {
                String[] headersToParse = ((com.lxw.retrofit.http.Headers) annotation).value();
                if (headersToParse.length == 0) {
                    throw methodError("@Headers annotation is empty.");
                }
            } else if (annotation instanceof Multipart) {
                if (isFormEncoded) {
                    throw methodError("Only one encoding annotation is allowed.");
                }
                isMultipart = true;
            } else if (annotation instanceof FormUrlEncoded) {
                if (isMultipart) {
                    throw methodError("Only one encoding annotation is allowed.");
                }
                isFormEncoded = true;
            }
        }


        private void parseHttpMethodAndPath(String httpMethod, String value, boolean hasBody) {
            if (this.httpMethod != null) {
                throw methodError("Only one HTTP method is allowed. Found: %s and %s.",
                        this.httpMethod, httpMethod);
            }
            this.httpMethod = httpMethod;
            this.hasBody = hasBody;
            int question = value.indexOf('?');
            if (question != -1 && question < value.length() - 1) {
                //todo 因为retrofit使用的使用在get post这些注解中的value允许 {xx} 配合@Path注解替换地址
                //todo 但是?后面不能写这样的字符串
                String queryParams = value.substring(question + 1);
                Matcher matcher = PARAM_URL_REGEX.matcher(queryParams);
                if (matcher.find()) {
                    throw methodError("URL query string \"%s\" must not have replace block. "
                            + "For dynamic query parameters use @Query.", queryParams);
                }
            }
            this.relativeUrl = value;
            //todo 解析出地址中的参数 {xx} （?之前的）
            this.relativeUrlParamNames = parsePathParameters(value);

        }

        private Set<String> parsePathParameters(String path) {
            Matcher m = PARAM_URL_REGEX.matcher(path);
            Set<String> patterns = new LinkedHashSet<>();
            while (m.find()) {
                patterns.add(m.group(1));
            }
            return patterns;
        }


        private Converter<ResponseBody, T> createResponseConverter() {
            Annotation[] annotations = method.getAnnotations();
            return retrofit.responseBodyConverter(responseType,annotations);
        }

        private CallAdapter<T, R> createCallAdapter() {
            Type returnType = method.getGenericReturnType();
            if (Utils.hasUnresolvableType(returnType)) {
                throw methodError(
                        "Method return type must not include a type variable or wildcard: %s",
                        returnType);
            }

            if (returnType == void.class) {
                throw methodError("Service methods cannot return void.");
            }
            Annotation[] annotations = method.getAnnotations();

            return (CallAdapter<T, R>) retrofit.callAdapter(returnType,annotations);
        }

        private RuntimeException methodError(String message, Object... args) {
            return methodError(null, message, args);
        }

        private RuntimeException methodError(Throwable cause, String message, Object... args) {
            message = String.format(message, args);
            return new IllegalArgumentException(message
                    + "\n    for method "
                    + method.getDeclaringClass().getSimpleName()
                    + "."
                    + method.getName(), cause);
        }

        private RuntimeException parameterError(
                Throwable cause, int p, String message, Object... args) {
            return methodError(cause, message + " (parameter #" + (p + 1) + ")", args);
        }

        private RuntimeException parameterError(int p, String message, Object... args) {
            return methodError(message + " (parameter #" + (p + 1) + ")", args);
        }

    }

    static Class<?> boxIfPrimitive(Class<?> type) {
        if (boolean.class == type) {
            return Boolean.class;
        }
        if (byte.class == type) {
            return Byte.class;
        }
        if (char.class == type) {
            return Character.class;
        }
        if (double.class == type) {
            return Double.class;
        }
        if (float.class == type) {
            return Float.class;
        }
        if (int.class == type) {
            return Integer.class;
        }
        if (long.class == type) {
            return Long.class;
        }
        if (short.class == type) {
            return Short.class;
        }
        return type;
    }

}
