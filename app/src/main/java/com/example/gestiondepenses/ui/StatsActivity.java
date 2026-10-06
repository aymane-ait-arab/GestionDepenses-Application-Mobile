package com.example.gestiondepenses.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.example.gestiondepenses.viewmodel.ExpenseViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsActivity extends AppCompatActivity {

    public enum TimePeriod {
        WEEK, MONTH, YEAR
    }

    private ExpenseViewModel expenseViewModel;
    private PieChart pieChart;
    private BarChart barChart;
    private RecyclerView rvCategoryLegend;
    private List<Category> categoryList = new ArrayList<>();
    private List<Expense> expenseList = new ArrayList<>();
    private TimePeriod currentPeriod = TimePeriod.MONTH;
    private String currencySymbol = "DH"; // Default currency

    // Summary Views
    private TextView tvTotalAmount, tvTransactionCount, tvTopCategory, tvTopCategoryAmount, tvTopCategoryPercent;
    private ImageView ivTopCategoryIcon;
    private MaterialCardView topCategoryIconContainer;
    private LinearLayout emptyState;
    private LinearLayout chartsContainer;

    // Period Buttons
    private MaterialButton btnWeek, btnMonth, btnYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        initViews();
        setupToolbar();
        setupPeriodButtons();
        setupRecyclerView();

        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);

        // Load user's currency preference
        currencySymbol = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("currency_symbol", "DH");

        expenseViewModel.getAllCategories().observe(this, categories -> {
            categoryList = categories != null ? categories : new ArrayList<>();
            updateStats();
        });

        expenseViewModel.getAllExpenses().observe(this, expenses -> {
            expenseList = expenses != null ? expenses : new ArrayList<>();
            updateStats();
        });
    }

    private void initViews() {
        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        rvCategoryLegend = findViewById(R.id.rvCategoryLegend);

        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvTransactionCount = findViewById(R.id.tvTransactionCount);
        tvTopCategory = findViewById(R.id.tvTopCategory);
        tvTopCategoryAmount = findViewById(R.id.tvTopCategoryAmount);
        tvTopCategoryPercent = findViewById(R.id.tvTopCategoryPercent);
        ivTopCategoryIcon = findViewById(R.id.ivTopCategoryIcon);
        topCategoryIconContainer = findViewById(R.id.topCategoryIconContainer);
        emptyState = findViewById(R.id.emptyState);
        chartsContainer = findViewById(R.id.chartsContainer);

        btnWeek = findViewById(R.id.btnWeek);
        btnMonth = findViewById(R.id.btnMonth);
        btnYear = findViewById(R.id.btnYear);
    }

    private void setupToolbar() {
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
    }

    private void setupPeriodButtons() {
        btnWeek.setOnClickListener(v -> setPeriod(TimePeriod.WEEK));
        btnMonth.setOnClickListener(v -> setPeriod(TimePeriod.MONTH));
        btnYear.setOnClickListener(v -> setPeriod(TimePeriod.YEAR));
        updatePeriodButtons();
    }

    private void setPeriod(TimePeriod period) {
        currentPeriod = period;
        updatePeriodButtons();
        updateStats();
    }

    private void updatePeriodButtons() {
        updateButtonStyle(btnWeek, currentPeriod == TimePeriod.WEEK);
        updateButtonStyle(btnMonth, currentPeriod == TimePeriod.MONTH);
        updateButtonStyle(btnYear, currentPeriod == TimePeriod.YEAR);
    }

    private void updateButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
            button.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
            button.setStrokeWidth(0);
        } else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.transparent));
            button.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            button.setStrokeWidth(0);  // Remove the stroke/border
        }
    }

    private void setupRecyclerView() {
        rvCategoryLegend.setLayoutManager(new LinearLayoutManager(this));
        rvCategoryLegend.setNestedScrollingEnabled(false);
    }

    private List<Expense> filterExpensesByPeriod(List<Expense> expenses) {
        List<Expense> filtered = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        long now = System.currentTimeMillis();

        for (Expense e : expenses) {
            if (e.date == 0) continue;

            boolean include = false;
            Calendar expenseCal = Calendar.getInstance();
            expenseCal.setTimeInMillis(e.date);
            switch (currentPeriod) {
                case WEEK:
                    include = isSameWeek(cal, expenseCal);
                    break;
                case MONTH:
                    include = isSameMonth(cal, expenseCal);
                    break;
                case YEAR:
                    include = isSameYear(cal, expenseCal);
                    break;
            }
            if (include) filtered.add(e);
        }
        return filtered;
    }

    private boolean isSameWeek(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.WEEK_OF_YEAR) == c2.get(Calendar.WEEK_OF_YEAR);
    }

    private boolean isSameMonth(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH);
    }

    private boolean isSameYear(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR);
    }

    private void updateStats() {
        if (categoryList.isEmpty() || expenseList.isEmpty()) {
            showEmptyState(true);
            return;
        }

        List<Expense> filtered = filterExpensesByPeriod(expenseList);
        if (filtered.isEmpty()) {
            showEmptyState(true);
            return;
        }

        showEmptyState(false);
        updateSummaryCards(filtered);
        setupPieChart(filtered);
        setupBarChart(filtered);
    }

    private void showEmptyState(boolean show) {
        if (show) {
            chartsContainer.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            chartsContainer.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void updateSummaryCards(List<Expense> expenses) {
        double total = 0;
        Map<String, Double> categoryAmounts = new HashMap<>();

        for (Expense e : expenses) {
            total += e.amount;
            if (e.categoryId != null) {
                categoryAmounts.put(e.categoryId,
                    categoryAmounts.getOrDefault(e.categoryId, 0.0) + e.amount);
            }
        }

        tvTotalAmount.setText(String.format(Locale.getDefault(), "%.0f %s", total, currencySymbol));
        tvTransactionCount.setText(String.valueOf(expenses.size()));

        // Find top category
        String topCatId = null;
        double topAmount = 0;
        for (Map.Entry<String, Double> entry : categoryAmounts.entrySet()) {
            if (entry.getValue() > topAmount) {
                topAmount = entry.getValue();
                topCatId = entry.getKey();
            }
        }

        if (topCatId != null && total > 0) {
            Category topCat = findCategory(topCatId);
            if (topCat != null) {
                tvTopCategory.setText(topCat.name);
                tvTopCategoryAmount.setText(String.format(Locale.getDefault(), "%.0f %s", topAmount, currencySymbol));
                int percent = (int) ((topAmount / total) * 100);
                tvTopCategoryPercent.setText(percent + "%");

                try {
                    int color = Color.parseColor(topCat.colorHex);
                    topCategoryIconContainer.setCardBackgroundColor(color);
                    ivTopCategoryIcon.setColorFilter(Color.WHITE);
                    tvTopCategoryPercent.setTextColor(color);
                    tvTopCategoryAmount.setTextColor(color);
                } catch (Exception e) {
                    topCategoryIconContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_container));
                }
            }
        } else {
            tvTopCategory.setText("-");
            tvTopCategoryAmount.setText("0 " + currencySymbol);
            tvTopCategoryPercent.setText("0%");
        }
    }

    private Category findCategory(String id) {
        for (Category c : categoryList) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    private void setupPieChart(List<Expense> expenses) {
        Map<String, Float> categoryTotals = new HashMap<>();

        for (Expense e : expenses) {
            if (e.categoryId != null) {
                float currentTotal = categoryTotals.getOrDefault(e.categoryId, 0f);
                categoryTotals.put(e.categoryId, currentTotal + (float) e.amount);
            }
        }

        List<PieEntry> pieEntries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<CategoryLegendItem> legendItems = new ArrayList<>();
        double total = 0;

        for (Category c : categoryList) {
            if (categoryTotals.containsKey(c.id)) {
                float amount = categoryTotals.get(c.id);
                total += amount;
                pieEntries.add(new PieEntry(amount, c.name));

                int color;
                try {
                    color = Color.parseColor(c.colorHex);
                } catch (Exception e) {
                    color = ContextCompat.getColor(this, R.color.primary);
                }
                colors.add(color);
                legendItems.add(new CategoryLegendItem(c.name, amount, color));
            }
        }

        // Sort legend by amount descending
        Collections.sort(legendItems, (a, b) -> Float.compare(b.amount, a.amount));
        final double finalTotal = total;
        rvCategoryLegend.setAdapter(new CategoryLegendAdapter(legendItems, (float) total, currencySymbol));

        if (pieEntries.isEmpty()) {
            pieChart.clear();
            return;
        }

        PieDataSet dataSet = new PieDataSet(pieEntries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);
        dataSet.setSliceSpace(4f);
        dataSet.setSelectionShift(8f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value > finalTotal * 0.1) {
                    return String.format(Locale.getDefault(), "%.0f", value);
                }
                return "";
            }
        });

        pieChart.setData(data);
        pieChart.setCenterText(String.format(Locale.getDefault(), "Total\n%.0f %s", total, currencySymbol));
        pieChart.setCenterTextSize(15f);
        pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
        pieChart.setCenterTextColor(ContextCompat.getColor(this, R.color.on_surface));
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(30);
        pieChart.getDescription().setEnabled(false);

        Legend legend = pieChart.getLegend();
        legend.setEnabled(false);

        pieChart.setEntryLabelTextSize(0f);
        pieChart.setDrawEntryLabels(false);
        pieChart.animateY(800, com.github.mikephil.charting.animation.Easing.EaseOutCirc);
        pieChart.invalidate();
    }

    private void setupBarChart(List<Expense> expenses) {
        List<BarEntry> barEntries = new ArrayList<>();
        final List<String> descriptions = new ArrayList<>();
        final List<Double> amounts = new ArrayList<>();

        int count = Math.min(expenses.size(), 10);
        List<Expense> recent = new ArrayList<>(expenses);
        Collections.sort(recent, (a, b) -> Long.compare(b.date, a.date));

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());

        for (int i = 0; i < count; i++) {
            Expense expense = recent.get(i);
            barEntries.add(new BarEntry(i, (float) expense.amount));
            descriptions.add(expense.description);
            amounts.add(expense.amount);
        }

        BarDataSet set = new BarDataSet(barEntries, "");
        int primaryColor = ContextCompat.getColor(this, R.color.primary);
        set.setColor(primaryColor);
        set.setGradientColor(
            Color.argb(100, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            primaryColor
        );
        set.setValueTextSize(10f);
        set.setValueTextColor(ContextCompat.getColor(this, R.color.on_surface));
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f", value);
            }
        });

        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        barChart.setData(data);
        barChart.setFitBars(true);
        barChart.getDescription().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawLabels(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.argb(20, 0, 0, 0));
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);

        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false);

        barChart.getLegend().setEnabled(false);

        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                if (index >= 0 && index < descriptions.size()) {
                    String desc = descriptions.get(index);
                    String amt = String.format(Locale.getDefault(), "%.2f %s", amounts.get(index), currencySymbol);
                    Toast.makeText(StatsActivity.this, desc + "\n" + amt, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected() {}
        });

        barChart.setTouchEnabled(true);
        barChart.setHighlightPerTapEnabled(true);
        barChart.setDrawBorders(false);
        barChart.setDrawGridBackground(false);

        barChart.animateY(600, com.github.mikephil.charting.animation.Easing.EaseOutQuad);
        barChart.invalidate();
    }

    // Category Legend Data Class
    public static class CategoryLegendItem {
        public final String name;
        public final float amount;
        public final int color;

        public CategoryLegendItem(String name, float amount, int color) {
            this.name = name;
            this.amount = amount;
            this.color = color;
        }
    }

    // Adapter for category legend
    public static class CategoryLegendAdapter extends RecyclerView.Adapter<CategoryLegendAdapter.ViewHolder> {
        private final List<CategoryLegendItem> items;
        private final float total;
        private final String currencySymbol;

        public CategoryLegendAdapter(List<CategoryLegendItem> items, float total, String currencySymbol) {
            this.items = items;
            this.total = total;
            this.currencySymbol = currencySymbol;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_legend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CategoryLegendItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvAmount.setText(String.format(Locale.getDefault(), "%.0f %s", item.amount, currencySymbol));

            int percent = total > 0 ? (int) ((item.amount / total) * 100) : 0;
            holder.tvPercent.setText(percent + "%");

            holder.colorIndicator.setBackgroundColor(item.color);

            android.widget.ProgressBar progress = holder.progressBar;
            progress.setProgress(percent);
            progress.getProgressDrawable().setColorFilter(item.color, android.graphics.PorterDuff.Mode.SRC_IN);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvAmount, tvPercent;
            View colorIndicator;
            android.widget.ProgressBar progressBar;

            public ViewHolder(android.view.View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvLegendName);
                tvAmount = itemView.findViewById(R.id.tvLegendAmount);
                tvPercent = itemView.findViewById(R.id.tvLegendPercent);
                colorIndicator = itemView.findViewById(R.id.colorIndicator);
                progressBar = itemView.findViewById(R.id.progressBar);
            }
        }
    }
}