package com.example.helios.ui;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class AppNavigator {

    private final AppCompatActivity activity;
    private final int containerId;

    public AppNavigator(AppCompatActivity activity, @IdRes int containerId) {
        this.activity = activity;
        this.containerId = containerId;
    }

    /**
     * Opens the main Event List screen
     */
    public void openEntrantHome() {
        replace(new EventListFragment(), false);
    }

    /**
     * Replaces the fragment inside the container
     */
    public void replace(Fragment fragment, boolean addToBackStack) {

        FragmentTransaction tx = activity
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(containerId, fragment, fragment.getClass().getSimpleName());

        if (addToBackStack) {
            tx.addToBackStack(fragment.getClass().getSimpleName());
        }

        tx.commit();
    }
}