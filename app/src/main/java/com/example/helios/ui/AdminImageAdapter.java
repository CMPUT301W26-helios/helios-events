package com.example.helios.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.helios.R;
import com.example.helios.model.Event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying event poster images in the admin panel.
 * Each item shows the poster thumbnail, the event title, its start date, and a
 * Delete button that removes the poster URI from the event (without deleting the
 * event itself).
 */
public class AdminImageAdapter extends RecyclerView.Adapter<AdminImageAdapter.ViewHolder> {

    public interface OnImageDeleteListener {
        void onImageDelete(@NonNull Event event);
    }

    private final List<Event> events;
    private final OnImageDeleteListener onDelete;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public AdminImageAdapter(
            @NonNull List<Event> events,
            @NonNull OnImageDeleteListener onDelete
    ) {
        this.events = events;
        this.onDelete = onDelete;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.admin_item_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);

        // Poster preview
        String posterUri = event.getPosterImageId();
        if (posterUri != null && !posterUri.trim().isEmpty()) {
            try {
                holder.ivPreview.setImageURI(Uri.parse(posterUri));
            } catch (Exception ignored) {
                holder.ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            if (holder.ivPreview.getDrawable() == null) {
                holder.ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.ivPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // Event title
        String title = event.getTitle();
        holder.tvLabel.setText(title != null && !title.trim().isEmpty() ? title : "Untitled Event");


        holder.btnDelete.setOnClickListener(v -> onDelete.onImageDelete(event));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivPreview;
        final TextView tvLabel;
        final Button btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.iv_image_preview);
            tvLabel   = itemView.findViewById(R.id.tv_image_label);
            btnDelete = itemView.findViewById(R.id.btn_delete_image);
        }
    }
}