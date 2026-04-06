package com.example.helios.ui;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.helios.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class ProfileSetupActivityTest {

    private ActivityScenario<ProfileSetupActivity> launchInMode(String mode) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, ProfileSetupActivity.class);
        intent.putExtra(ProfileSetupActivity.EXTRA_MODE, mode);
        intent.putExtra(ProfileSetupActivity.EXTRA_RETURN_TO_MAIN, false);
        return ActivityScenario.launch(intent);
    }

    @Test
    public void setupRequiredMode_showsRequiredCopy_andStartsWithDisabledButton() {
        try (ActivityScenario<ProfileSetupActivity> ignored =
                     launchInMode(ProfileSetupActivity.MODE_SETUP_REQUIRED)) {

            onView(withId(R.id.submenu_title))
                    .check(matches(withText("Profile Required")));
            onView(withId(R.id.submenu_subtitle))
                    .check(matches(withText(
                            "Name and email are required before signing up for events or organizing them."
                    )));
            onView(withId(R.id.button_sign_up))
                    .check(matches(withText("Save  >")));
            onView(withId(R.id.button_sign_up))
                    .check(matches(not(isEnabled())));
        }
    }

    @Test
    public void signUpButton_isEnabledOnlyWhenNameAndEmailAreProvided() {
        try (ActivityScenario<ProfileSetupActivity> ignored =
                     launchInMode(ProfileSetupActivity.MODE_SETUP_REQUIRED)) {

            onView(withId(R.id.button_sign_up))
                    .check(matches(not(isEnabled())));

            onView(withId(R.id.edit_name))
                    .perform(replaceText("Alice"), closeSoftKeyboard());
            onView(withId(R.id.button_sign_up))
                    .check(matches(not(isEnabled())));

            onView(withId(R.id.edit_email))
                    .perform(replaceText("alice@example.com"), closeSoftKeyboard());
            onView(withId(R.id.button_sign_up))
                    .check(matches(isEnabled()));
        }
    }

    @Test
    public void clearingARequiredField_disablesSignUpButtonAgain() {
        try (ActivityScenario<ProfileSetupActivity> ignored =
                     launchInMode(ProfileSetupActivity.MODE_SETUP_REQUIRED)) {

            onView(withId(R.id.edit_name))
                    .perform(replaceText("Alice"), closeSoftKeyboard());
            onView(withId(R.id.edit_email))
                    .perform(replaceText("alice@example.com"), closeSoftKeyboard());
            onView(withId(R.id.button_sign_up))
                    .check(matches(isEnabled()));

            onView(withId(R.id.edit_name))
                    .perform(clearText(), closeSoftKeyboard());
            onView(withId(R.id.button_sign_up))
                    .check(matches(not(isEnabled())));
        }
    }

    @Test
    public void editProfileMode_updatesTitleSubtitleAndPrimaryButtonText() {
        try (ActivityScenario<ProfileSetupActivity> ignored =
                     launchInMode(ProfileSetupActivity.MODE_EDIT_PROFILE)) {

            onView(withId(R.id.profile_setup_title))
                    .check(matches(withText("Edit Profile")));
            onView(withId(R.id.profile_setup_subtitle))
                    .check(matches(withText("Update your info below.")));
            onView(withId(R.id.button_sign_up))
                    .check(matches(withText("Save")));
        }
    }

    @Test
    public void phoneRemainsOptionalWhenRequiredFieldsArePresent() {
        try (ActivityScenario<ProfileSetupActivity> ignored =
                     launchInMode(ProfileSetupActivity.MODE_SETUP_REQUIRED)) {

            onView(withId(R.id.edit_name))
                    .perform(replaceText("Sam Man"), closeSoftKeyboard());
            onView(withId(R.id.edit_email))
                    .perform(replaceText("sam.man@example.com"), closeSoftKeyboard());

            onView(withId(R.id.button_sign_up))
                    .check(matches(isEnabled()));
        }
    }
}