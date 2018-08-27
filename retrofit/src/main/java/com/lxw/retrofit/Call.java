package com.lxw.retrofit;

import java.io.Closeable;
import java.io.IOException;

import okhttp3.Request;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/23
 *     desc   :
 * </pre>
 */
public interface Call<T> extends Closeable {

    Response<T> execute() throws Throwable;

    void enquue(Callback<T> callback);

    boolean isExecuted();

    void cancel();

    boolean isCanceled();

    Call<T> clone();

    Request request();

}
