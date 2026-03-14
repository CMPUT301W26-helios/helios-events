package com.example.helios.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.UserProfile;
import android.widget.Button;
import java.util.List;

/**
 * RecyclerView adapter for displaying a list of users, typically for administrative purposes.
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    /**
     * Listener interface for handling user-related click events.
     */
    public interface OnUserClickListener {
        /**
         * Called when a user's delete button is clicked.
         * @param user The user profile associated with the clicked item.
         */
        void onUserClick(@NonNull UserProfile user);
    }

    private final List<UserProfile> users;
    private final OnUserClickListener onClick;

    /**
     * Constructs a UserAdapter.
     *
     * @param users   The list of user profiles to display.
     * @param onClick The listener for click events.
     */
    public UserAdapter(@NonNull List<UserProfile> users, @NonNull OnUserClickListener onClick) {
        this.users = users;
        this.onClick = onClick;
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
        holder.tvRole.setText(user.getRole() != null ? user.getRole() : "user");

        // Delete button click — no need to click the whole row
        holder.btnDelete.setOnClickListener(v -> onClick.onUserClick(user));
    }

    @Override
    public int getItemCount() { return users.size(); }

    /**
     * ViewHolder class for user items.
     */
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvRole;
        Button btnDelete;

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
        }
    }
}
