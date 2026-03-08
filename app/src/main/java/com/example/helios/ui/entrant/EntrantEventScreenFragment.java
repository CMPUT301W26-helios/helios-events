package com.example.helios.ui.entrant;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.helios.R;
import com.example.helios.ui.MainActivity;

public class EntrantEventScreenFragment extends Fragment {

    public EntrantEventScreenFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_event_screen, container, false);
    }
/*
@Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View browseButton = view.findViewById(R.id.button_browse_events);
        View signUpButton = view.findViewById(R.id.button_try_sign_up);
        View organizerButton = view.findViewById(R.id.button_open_organizer);

        browseButton.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Browsing events is allowed without profile setup.", Toast.LENGTH_SHORT).show()
        );

        signUpButton.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            activity.requireProfileForComplexAction(() ->
                    Toast.makeText(requireContext(), "Event sign-up would continue here.", Toast.LENGTH_SHORT).show()
            );
        });

        organizerButton.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            activity.requireProfileForComplexAction(() ->
                    Toast.makeText(requireContext(), "Organizer menu would open here.", Toast.LENGTH_SHORT).show()
            );
        });
    }
 */

}