package com.example.gestiondepenses.ui.adapter;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.example.gestiondepenses.data.model.Expense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    private List<Expense> expenses = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private Map<String, Category> categoryMap = new HashMap<>();
    private String currencySymbol;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Expense expense);
        void onItemLongClick(Expense expense);
    }

    public ExpenseAdapter(Context context, OnItemClickListener listener) {
        SharedPreferences sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        this.currencySymbol = sharedPref.getString("currency_symbol", "DH");
        this.listener = listener;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses;
        notifyDataSetChanged();
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        this.categoryMap.clear();
        for (Category category : categories) {
            if (category.id != null) {
                this.categoryMap.put(category.id, category);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense currentExpense = expenses.get(position);

        // 1. Description
        holder.textViewDescription.setText(currentExpense.description);

        // 2. Montant (avec un signe moins pour un look plus bancaire)
        holder.textViewAmount.setText(String.format(Locale.getDefault(), "- %.2f %s", currentExpense.amount, currencySymbol));

        // 3. Date
        if (currentExpense.date != 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String dateFormatee = sdf.format(new Date(currentExpense.date));
            holder.textViewDate.setText(dateFormatee);
        } else {
            holder.textViewDate.setText("--/--/----");
        }

        // 4. Category styling (Clean modern design)
        setupCategoryStyling(holder, currentExpense);

        // 5. Gestion des clics
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(currentExpense);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(currentExpense);
                return true;
            }
            return false;
        });
    }

    private void setupCategoryStyling(ExpenseViewHolder holder, Expense expense) {
        Category category = categoryMap.get(expense.categoryId);

        int color;
        if (category != null && category.colorHex != null && !category.colorHex.isEmpty()) {
            try {
                color = Color.parseColor(category.colorHex);
            } catch (IllegalArgumentException e) {
                color = holder.itemView.getContext().getColor(R.color.primary);
            }
        } else {
            color = holder.itemView.getContext().getColor(R.color.primary);
        }

        // Set icon background with transparency (Le secret du design Apple/Revolut)
        holder.imgCategoryIcon.setBackgroundTintList(
                ColorStateList.valueOf(adjustAlpha(color, 0.15f))
        );
        holder.imgCategoryIcon.setColorFilter(color);

        // Optional: Set category initial or icon
        if (category != null && category.name != null && !category.name.isEmpty()) {
            // holder.imgCategoryIcon.setImageResource(getCategoryIcon(category.name));
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewDescription;
        private TextView textViewAmount;
        private TextView textViewDate;
        private ImageView imgCategoryIcon;

        public ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDescription = itemView.findViewById(R.id.text_view_description);
            textViewAmount = itemView.findViewById(R.id.text_view_amount);
            textViewDate = itemView.findViewById(R.id.text_view_date);
            imgCategoryIcon = itemView.findViewById(R.id.img_category_icon);
        }
    }
}