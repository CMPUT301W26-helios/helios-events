package com.example.helios.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.helios.HeliosApplication;
import com.example.helios.R;
import com.example.helios.ui.common.HeliosHeaderBranding;
import com.example.helios.service.AccessibilityPreferences;
import com.example.helios.ui.theme.HeliosThemeManager;

public abstract class ThemedActivity extends AppCompatActivity {
    private String appliedAppearanceSignature;
    private boolean fontCallbacksRegistered;
    private final FragmentManager.FragmentLifecycleCallbacks fontLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(
                        @NonNull FragmentManager fm,
                        @NonNull Fragment fragment,
                        @NonNull View view,
                        @Nullable Bundle savedInstanceState
                ) {
                    applyPreferredFontToView(view);
                    HeliosHeaderBranding.applyToView(
                            view,
                            HeliosApplication.from(view.getContext()).getUiPreferences().isHeaderIconEnabled()
                    );
                }
            };

    @Override
    protected void attachBaseContext(Context newBase) {
        HeliosApplication application = HeliosApplication.from(newBase);
        AccessibilityPreferences accessibilityPreferences = application.getAccessibilityPreferences();
        super.attachBaseContext(accessibilityPreferences.wrapContext(newBase));
    }

    protected final void applyHeliosTheme() {
        HeliosApplication application = HeliosApplication.from(this);
        AccessibilityPreferences accessibilityPreferences = application.getAccessibilityPreferences();
        application.getThemeManager().applyTheme(this, accessibilityPreferences);
        appliedAppearanceSignature = application.getThemeManager()
                .createAppearanceSignature(accessibilityPreferences);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        applyPreferredFontToContent();
        refreshHeaderBranding();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        applyPreferredFontToContent();
        registerFontCallbacksIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHeaderBranding();

        if (appliedAppearanceSignature == null) {
            return;
        }

        HeliosApplication application = HeliosApplication.from(this);
        String currentAppearanceSignature = application.getThemeManager()
                .createAppearanceSignature(application.getAccessibilityPreferences());
        if (!appliedAppearanceSignature.equals(currentAppearanceSignature)) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        if (fontCallbacksRegistered) {
            getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(fontLifecycleCallbacks);
            fontCallbacksRegistered = false;
        }
        super.onDestroy();
    }

    private void registerFontCallbacksIfNeeded() {
        if (fontCallbacksRegistered) {
            return;
        }
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(fontLifecycleCallbacks, true);
        fontCallbacksRegistered = true;
    }

    private void applyPreferredFontToContent() {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            applyPreferredFontToView(root);
        }
    }

    protected final void applyPreferredFontToView(@NonNull View root) {
        HeliosThemeManager themeManager = HeliosApplication.from(this).getThemeManager();
        applyPreferredFontRecursively(root, themeManager.getTypeface());
    }

    public final void refreshHeaderBranding() {
        HeliosHeaderBranding.applyToActivity(this);
    }

    private void applyPreferredFontRecursively(@NonNull View view, @NonNull Typeface typeface) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface currentTypeface = textView.getTypeface();
            int style = currentTypeface != null ? currentTypeface.getStyle() : Typeface.NORMAL;
            textView.setTypeface(typeface, style);
        }

        if (view instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) view;
            if (recyclerView.getTag(R.id.tag_font_listener_registered) == null) {
                recyclerView.addOnChildAttachStateChangeListener(
                        new RecyclerView.OnChildAttachStateChangeListener() {
                            @Override
                            public void onChildViewAttachedToWindow(@NonNull View child) {
                                applyPreferredFontRecursively(child, typeface);
                            }

                            @Override
                            public void onChildViewDetachedFromWindow(@NonNull View child) {
                            }
                        }
                );
                recyclerView.setTag(R.id.tag_font_listener_registered, Boolean.TRUE);
            }
        }

        if (view instanceof BottomNavigationView) {
            // BottomNav has complex internal labels; re-traversing ensures they are caught
            // even if they were added/modified after the initial pass.
            BottomNavigationView bnv = (BottomNavigationView) view;
            bnv.post(() -> applyPreferredFontRecursively(bnv, typeface));
        }

        if (!(view instanceof ViewGroup)) {
            return;
        }

        ViewGroup group = (ViewGroup) view;

        // Register a listener for dynamically added children (like Chips in ProfileFragment)
        if (group.getTag(R.id.tag_font_hierarchy_listener_registered) == null) {
            group.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    applyPreferredFontRecursively(child, typeface);
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                }
            });
            group.setTag(R.id.tag_font_hierarchy_listener_registered, Boolean.TRUE);
        }

        for (int index = 0; index < group.getChildCount(); index++) {
            applyPreferredFontRecursively(group.getChildAt(index), typeface);
        }
    }
}
