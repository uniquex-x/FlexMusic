package com.example.flexmusicplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.example.flexmusicplayer.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'flexmusicplayer' library on application startup.
    static {
        System.loadLibrary("flexmusicplayer");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


    }

}