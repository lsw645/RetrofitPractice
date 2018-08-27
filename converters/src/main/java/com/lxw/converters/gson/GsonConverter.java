package com.lxw.converters.gson;

import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.lxw.retrofit.Converter;
import com.lxw.retrofit.Retrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/27
 *     desc   :
 * </pre>
 */
public class GsonConverter extends Converter.Factory {
    private final Gson gson;

    public static GsonConverter create() {
        return new GsonConverter();
    }

    private GsonConverter() {
        gson = new Gson();
    }

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        TypeAdapter<?> typeAdapter = gson.getAdapter(TypeToken.get(type));
        return new GsonResponseBodyConverter<>(gson, typeAdapter);
    }

    @Nullable
    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(type));
        return new GsonRequestBodyConverter<>(gson, adapter);
    }


}
