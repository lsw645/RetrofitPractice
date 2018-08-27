package com.lxw.retrofitpractice;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.lxw.converters.gson.GsonConverter;
import com.lxw.retrofit.Call;
import com.lxw.retrofit.Callback;
import com.lxw.retrofit.Response;
import com.lxw.retrofit.Retrofit;

import java.util.List;

import static com.lxw.retrofitpractice.SimpleService.API_URL;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    public void click(View view) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addConverterFactory(GsonConverter.create())
                .build();

        SimpleService.GitHub github = retrofit.create(SimpleService.GitHub.class);
        System.out.println(github);

        Call<List<SimpleService.Contributor>> call = github.contributors("square", "retrofit");

        try {

            call.enquue(new Callback<List<SimpleService.Contributor>>() {
                @Override
                public void onResponse(Call<List<SimpleService.Contributor>> call, Response<List<SimpleService.Contributor>> response) {
                    for (SimpleService.Contributor contributor : response.body()) {
                        System.out.println(contributor.login + " (" + contributor.contributions + ")");
                    }
                }

                @Override
                public void onFailure(Call<List<SimpleService.Contributor>> call, Throwable t) {

                }
            });

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }
}
