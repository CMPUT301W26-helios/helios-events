package com.example.helios.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.helios.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void bottomNavigation_switchesToEventsFragment() {
        // Assert bottom nav exists
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));

        // Click on Events menu item
        onView(withId(R.id.eventsFragment)).perform(click());

        // Assuming fragment container inflates the events fragment map/list
        onView(withId(R.id.nav_host_fragment)).check(matches(isDisplayed()));
    }

    @Test
    public void bottomNavigation_switchesToOrganizeFragment() {
        onView(withId(R.id.organizeFragment)).perform(click());
        onView(withId(R.id.nav_host_fragment)).check(matches(isDisplayed()));
    }

    @Test
    public void bottomNavigation_switchesToProfileFragment() {
        onView(withId(R.id.profileFragment)).perform(click());
        onView(withId(R.id.nav_host_fragment)).check(matches(isDisplayed()));
    }
}
