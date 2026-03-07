package com.example.helios.data;

import com.example.helios.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class EventService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccessListener,
                             OnFailureListener onFailureListener) {
        db.collection("events")
                .orderBy("startDate", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Event> events = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Event event = document.toObject(Event.class);

                        // Store Firestore document id into model if needed
                        event.setId(document.getId());

                        events.add(event);
                    }

                    onSuccessListener.onSuccess(events);
                })
                .addOnFailureListener(onFailureListener);
    }
}