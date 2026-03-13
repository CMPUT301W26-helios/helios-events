package com.example.helios.ui;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.helios.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class ProfileSetupActivityTest {

    @Rule
    public ActivityScenarioRule<ProfileSetupActivity> activityRule =
            new ActivityScenarioRule<>(ProfileSetupActivity.class);

    @Test
    public void signUpButton_isEnabledOnlyWhenNameAndEmailAreProvided() {
        onView(withId(R.id.button_sign_up)).check(matches(not(isEnabled())));

        onView(withId(R.id.edit_name))
                .perform(typeText("Alice"), closeSoftKeyboard());
        onView(withId(R.id.button_sign_up)).check(matches(not(isEnabled())));

        onView(withId(R.id.edit_email))
                .perform(typeText("alice@example.com"), closeSoftKeyboard());
        onView(withId(R.id.button_sign_up)).check(matches(isEnabled()));
    }

    @Test
    public void clearingARequiredField_disablesSignUpButtonAgain() {
        onView(withId(R.id.edit_name))
                .perform(typeText("Alice"), closeSoftKeyboard());
        onView(withId(R.id.edit_email))
                .perform(typeText("alice@example.com"), closeSoftKeyboard());
        onView(withId(R.id.button_sign_up)).check(matches(isEnabled()));

        onView(withId(R.id.edit_name))
                .perform(clearText(), closeSoftKeyboard());
        onView(withId(R.id.button_sign_up)).check(matches(not(isEnabled())));
    }
}
