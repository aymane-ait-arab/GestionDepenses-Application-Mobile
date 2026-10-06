package com.example.gestiondepenses.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categories = new ArrayList<>();
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onDeleteClick(Category category);
        void onEditClick(Category category);
    }

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category currentCategory = categories.get(position);

        // Set category name
        holder.tvCategoryName.setText(currentCategory.name);

        // Set category color
        int color;
        try {
            color = Color.parseColor(currentCategory.colorHex);
        } catch (Exception e) {
            color = holder.itemView.getContext().getColor(R.color.primary);
        }

        // Apply color to card container background
        holder.iconContainer.setCardBackgroundColor(ColorStateList.valueOf(color));

        // Set icon tint based on brightness (white for dark colors, dark for light)
        holder.viewCategoryColor.setColorFilter(isColorDark(color) ? Color.WHITE : Color.DKGRAY);

        // Show/hide delete button based on isDefault
        if (currentCategory.isDefault) {
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(null);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(currentCategory);
                }
            });
        }

        // Edit click on entire item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(currentCategory);
            }
        });
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    // ============================================
    // VIEW HOLDER
    // ============================================
    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private TextView tvCategoryName;
        private ImageView viewCategoryColor;
        private MaterialCardView iconContainer;
        private MaterialButton btnDelete;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
            viewCategoryColor = itemView.findViewById(R.id.view_category_color);
            iconContainer = itemView.findViewById(R.id.icon_container);
            btnDelete = itemView.findViewById(R.id.btn_delete_category);
        }
    }
}