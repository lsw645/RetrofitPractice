package com.lxw.calladapter;

import com.lxw.retrofit.Call;
import com.lxw.retrofit.Callback;
import com.lxw.retrofit.Response;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * <pre>
 *     author : lxw
 *     e-mail : lsw@tairunmh.com
 *     time   : 2018/08/27
 *     desc   :
 * </pre>
 */
public class CallEnqueueObservable<T> extends Observable<T> {
    private final Call<T> originalCall;

    public CallEnqueueObservable(Call<T> originalCall) {
        this.originalCall = originalCall;
    }

    @Override
    protected void subscribeActual(Observer observer) {
        Call<T> call = originalCall.clone();
        CallCallback<T> callback = new CallCallback<>(call, observer);
        observer.onSubscribe(callback);
        call.enquue(callback);
    }


    private static final class CallCallback<T> implements Disposable, Callback<T> {
        private final Call<?> call;
        private final Observer<? super Response<T>> observer;
        private volatile boolean disposed;
        boolean terminated = false;

        CallCallback(Call<?> call, Observer<? super Response<T>> observer) {
            this.call = call;
            this.observer = observer;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            if (disposed) return;

            try {
                observer.onNext(response);

                if (!disposed) {
                    terminated = true;
                    observer.onComplete();
                }
            } catch (Throwable t) {
                if (terminated) {
                    RxJavaPlugins.onError(t);
                } else if (!disposed) {
                    try {
                        observer.onError(t);
                    } catch (Throwable inner) {
                        Exceptions.throwIfFatal(inner);
                        RxJavaPlugins.onError(new CompositeException(t, inner));
                    }
                }
            }
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            if (call.isCanceled()) return;

            try {
                observer.onError(t);
            } catch (Throwable inner) {
                Exceptions.throwIfFatal(inner);
                RxJavaPlugins.onError(new CompositeException(t, inner));
            }
        }

        @Override
        public void dispose() {
            disposed = true;
            call.cancel();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
