package com.lxw.calladapter;

import com.lxw.retrofit.Call;
import com.lxw.retrofit.CallAdapter;

import java.lang.reflect.Type;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/27
 *     desc   :
 * </pre>
 */
public class RxJava2CallAdapter<R> implements CallAdapter<R, Object> {
    private final Type responseType;
    private final boolean isBody;
    private final boolean isFlowable;
    private final boolean isSingle;
    private final  boolean isMaybe;
    private final boolean isCompletable;

    public RxJava2CallAdapter(Type responseType, boolean isBody, boolean isFlowable, boolean isSingle, boolean isMaybe, boolean isCompletable) {
        this.responseType = responseType;
        this.isBody = isBody;
        this.isFlowable = isFlowable;
        this.isSingle = isSingle;
        this.isMaybe = isMaybe;
        this.isCompletable = isCompletable;
    }

    @Override
    public Type responseType() {
        return responseType;
    }

    @Override
    public Object adapt(Call<R> call) {
        return null;
    }
}
