package com.lxw.calladapter;

import android.support.annotation.Nullable;

import com.lxw.retrofit.CallAdapter;
import com.lxw.retrofit.Retrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import io.reactivex.Completable;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/27
 *     desc   :
 * </pre>
 */
public class RxJava2CallAdapterFactory extends CallAdapter.Factory {

    public static RxJava2CallAdapterFactory create() {
        return new RxJava2CallAdapterFactory();
    }

    @Nullable
    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        Class<?> rawType = getRawType(returnType);
        if(rawType.isAssignableFrom(Completable.class)){
            return new RxJava2CallAdapter<>();
        }


        return null;
    }

}
