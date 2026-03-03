package com.example.helios.ui;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.helios.ui.entrant.EntrantEventScreenFragment;

public class AppNavigator {
    private final AppCompatActivity activity;
    private final int containerId;

    public AppNavigator(AppCompatActivity activity, @IdRes int containerId) {
        this.activity = activity;
        this.containerId = containerId;
    }

    public void openEntrantHome() {
        replace(new EntrantEventScreenFragment(), false);
    }

    public void replace(Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction tx = activity
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(containerId, fragment);

        if (addToBackStack) {
            tx.addToBackStack(null);
        }

        tx.commit();
    }
}