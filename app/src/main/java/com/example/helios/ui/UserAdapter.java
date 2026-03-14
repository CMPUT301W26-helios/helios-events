package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.UserProfile;

import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for displaying a list of users, typically for administrative purposes.
 * Displays an elevated role label ("user & organizer") for users who have created events,
 * and provides dedicated buttons for viewing a user's events and deleting their account.
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    /**
     * Listener interface for handling the delete button click on a user item.
     */
    public interface OnUserDeleteListener {
        /**
         * Called when a user's delete button is clicked.
         * @param user The user profile associated with the clicked item.
         */
        void onUserDelete(@NonNull UserProfile user);
    }

    /**
     * Listener interface for handling the "View Events" button click on a user item.
     */
    public interface OnViewEventsListener {
        /**
         * Called when a user's "View Events" button is clicked.
         * @param user The user profile whose events should be shown.
         */
        void onViewEvents(@NonNull UserProfile user);
    }

    private final List<UserProfile> users;
    private final Set<String> organizerUids;
    private final OnUserDeleteListener onDelete;
    private final OnViewEventsListener onViewEvents;

    /**
     * Constructs a UserAdapter.
     *
     * @param users         The list of user profiles to display.
     * @param organizerUids Set of UIDs that have at least one event — used to show
     *                      "user & organizer" instead of plain "user" in the role label.
     * @param onDelete      The listener for delete button clicks.
     * @param onViewEvents  The listener for "View Events" button clicks.
     */
    public UserAdapter(
            @NonNull List<UserProfile> users,
            @NonNull Set<String> organizerUids,
            @NonNull OnUserDeleteListener onDelete,
            @NonNull OnViewEventsListener onViewEvents
    ) {
        this.users = users;
        this.organizerUids = organizerUids;
        this.onDelete = onDelete;
        this.onViewEvents = onViewEvents;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserProfile user = users.get(position);

        holder.tvName.setText(user.getName() != null ? user.getName() : "(no name)");
        holder.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "(no email)");

        // Elevate the role label when the user has also created events.
        String baseRole = user.getRole() != null ? user.getRole() : "user";
        boolean isOrganizer = organizerUids.contains(user.getUid());
        if (isOrganizer && "user".equals(baseRole)) {
            holder.tvRole.setText("user & organizer");
        } else if (isOrganizer && "admin".equals(baseRole)) {
            holder.tvRole.setText("admin & organizer");
        } else {
            holder.tvRole.setText(baseRole);
        }

        holder.btnDelete.setOnClickListener(v -> onDelete.onUserDelete(user));
        holder.btnViewEvents.setOnClickListener(v -> onViewEvents.onViewEvents(user));
    }

    @Override
    public int getItemCount() { return users.size(); }

    /**
     * ViewHolder class for user items.
     */
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvRole;
        Button btnDelete;
        Button btnViewEvents;

        /**
         * Constructs a UserViewHolder.
         * @param itemView The view for a single list item.
         */
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_user_name);
            tvEmail = itemView.findViewById(R.id.tv_user_email);
            tvRole = itemView.findViewById(R.id.tv_user_role);
            btnDelete = itemView.findViewById(R.id.btn_delete_user);
            btnViewEvents = itemView.findViewById(R.id.btn_view_events);
        }
    }
}