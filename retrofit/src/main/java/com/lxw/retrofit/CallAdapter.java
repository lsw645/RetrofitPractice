package com.lxw.retrofit;

import android.support.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/23
 *     desc   :将 response(R)类型转化为 T类型
 * </pre>
 */
public interface CallAdapter<R, T> {

    Type responseType();

    T adapt(Call<R> call);


    abstract class Factory {
        public abstract @Nullable
        CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit);

        protected static Type getParameterUpperBound(int index, ParameterizedType type) {
            return getParameterUpperBound(index, type);
        }


        protected static Class<?> getRawType(Type type) {
            return Utils.getRawType(type);
        }
    }

}
