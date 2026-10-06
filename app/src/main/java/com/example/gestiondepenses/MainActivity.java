package com.example.gestiondepenses;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.example.gestiondepenses.ui.AddExpenseActivity;
import com.example.gestiondepenses.ui.CategoryActivity;
import com.example.gestiondepenses.ui.LoginActivity;
import com.example.gestiondepenses.ui.ProfileActivity;
import com.example.gestiondepenses.ui.StatsActivity;
import com.example.gestiondepenses.ui.adapter.ExpenseAdapter;
import com.example.gestiondepenses.viewmodel.ExpenseViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private ExpenseViewModel mExpenseViewModel;
    private ExpenseAdapter adapter;
    private TextView tvTotalAmount, tvResultCount, tvActiveFilters, tvFilterIndicator, tvEmptySubtitle;
    private LinearLayout emptyState;
    private RecyclerView recyclerView;
    private MaterialCardView cardSummary;
    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigation;

    // Filter views
    private EditText editSearch;
    private ImageButton btnClearSearch;
    private Chip chipPeriod, chipCategory, chipClear;
    private ChipGroup chipGroupFilters;

    // Filter state
    private List<Expense> allExpenses = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();
    private String searchQuery = "";
    private long startDate = 0;
    private long endDate = 0;
    private Set<String> selectedCategoryIds = new HashSet<>();
    private boolean isFilterActive = false;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initializeViews();
        setupToolbar();
        setupRecyclerView();
        setupViewModel();
        setupFab();
        setupBottomNavigation();
        setupSearchAndFilters();
    }

    private void initializeViews() {
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvResultCount = findViewById(R.id.tv_result_count);
        tvActiveFilters = findViewById(R.id.tv_active_filters);
        tvFilterIndicator = findViewById(R.id.tv_filter_indicator);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);
        emptyState = findViewById(R.id.empty_state);
        recyclerView = findViewById(R.id.recycler_view);
        cardSummary = findViewById(R.id.card_summary);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Filter views
        editSearch = findViewById(R.id.edit_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        chipPeriod = findViewById(R.id.chip_period);
        chipCategory = findViewById(R.id.chip_category);
        chipClear = findViewById(R.id.chip_clear);
        chipGroupFilters = findViewById(R.id.chip_group_filters);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setNestedScrollingEnabled(false);

        adapter = new ExpenseAdapter(this, new ExpenseAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Expense expense) {
                Intent intent = new Intent(MainActivity.this, AddExpenseActivity.class);
                intent.putExtra("EXTRA_ID", expense.id);
                intent.putExtra("EXTRA_DESC", expense.description);
                intent.putExtra("EXTRA_AMOUNT", expense.amount);
                intent.putExtra("EXTRA_DATE", expense.date);
                intent.putExtra("EXTRA_CAT_ID", expense.categoryId);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(Expense expense) {
                showDeleteConfirmationDialog(expense);
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        mExpenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);

        mExpenseViewModel.getAllExpenses().observe(this, expenses -> {
            allExpenses = expenses != null ? expenses : new ArrayList<>();
            applyFilters();
        });

        mExpenseViewModel.getAllCategories().observe(this, categories -> {
            allCategories = categories != null ? categories : new ArrayList<>();
            adapter.setCategories(categories);
        });
    }

    private void setupSearchAndFilters() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            searchQuery = "";
            btnClearSearch.setVisibility(View.GONE);
        });

        chipPeriod.setOnClickListener(v -> showPeriodPicker());
        chipCategory.setOnClickListener(v -> showCategoryPicker());
        chipClear.setOnClickListener(v -> clearAllFilters());
    }

    private void showPeriodPicker() {
        Calendar now = Calendar.getInstance();

        DatePickerDialog startPicker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar startCal = Calendar.getInstance();
            startCal.set(year, month, day, 0, 0, 0);
            startDate = startCal.getTimeInMillis();

            DatePickerDialog endPicker = new DatePickerDialog(this, (view2, year2, month2, day2) -> {
                Calendar endCal = Calendar.getInstance();
                endCal.set(year2, month2, day2, 23, 59, 59);
                endDate = endCal.getTimeInMillis();

                chipPeriod.setChecked(true);
                applyFilters();

            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));

            endPicker.setTitle("Date de fin");
            endPicker.show();

        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));

        startPicker.setTitle("Date de début");
        startPicker.show();
    }

    private void showCategoryPicker() {
        if (allCategories.isEmpty()) {
            Toast.makeText(this, "Aucune catégorie disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] categoryNames = new String[allCategories.size()];
        boolean[] checkedItems = new boolean[allCategories.size()];

        for (int i = 0; i < allCategories.size(); i++) {
            categoryNames[i] = allCategories.get(i).name;
            checkedItems[i] = selectedCategoryIds.contains(allCategories.get(i).id);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sélectionner les catégories");

        builder.setMultiChoiceItems(categoryNames, checkedItems, (dialog, which, isChecked) -> {
            String catId = allCategories.get(which).id;
            if (isChecked) {
                selectedCategoryIds.add(catId);
            } else {
                selectedCategoryIds.remove(catId);
            }
        });

        builder.setPositiveButton("Appliquer", (dialog, which) -> {
            chipCategory.setChecked(!selectedCategoryIds.isEmpty());
            applyFilters();
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }

    private void clearAllFilters() {
        searchQuery = "";
        startDate = 0;
        endDate = 0;
        selectedCategoryIds.clear();

        editSearch.setText("");
        chipPeriod.setChecked(false);
        chipCategory.setChecked(false);

        applyFilters();
    }

    private void applyFilters() {
        List<Expense> filtered = allExpenses.stream().filter(expense -> {
            if (!searchQuery.isEmpty()) {
                String desc = expense.description != null ? expense.description.toLowerCase() : "";
                if (!desc.contains(searchQuery)) {
                    return false;
                }
            }
            if (startDate != 0 && endDate != 0) {
                if (expense.date < startDate || expense.date > endDate) {
                    return false;
                }
            }
            if (!selectedCategoryIds.isEmpty()) {
                if (!selectedCategoryIds.contains(expense.categoryId)) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        adapter.setExpenses(filtered);
        updateUI(filtered);
        updateFilterIndicator();
    }

    private void updateUI(List<Expense> expenses) {
        tvResultCount.setText(expenses.size() + " dépense" + (expenses.size() > 1 ? "s" : ""));

        if (expenses.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            cardSummary.setVisibility(isFilterActive ? View.VISIBLE : View.GONE);

            if (isFilterActive) {
                tvEmptySubtitle.setText("Aucun résultat pour vos critères");
                tvEmptySubtitle.setVisibility(View.VISIBLE);
            } else {
                tvEmptySubtitle.setVisibility(View.GONE);
            }
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            cardSummary.setVisibility(View.VISIBLE);

            double total = calculateTotal(expenses);
            tvTotalAmount.setText(formatCurrency(total));
        }
    }

    private void updateFilterIndicator() {
        isFilterActive = !searchQuery.isEmpty() || startDate != 0 || !selectedCategoryIds.isEmpty();

        chipClear.setVisibility(isFilterActive ? View.VISIBLE : View.GONE);
        tvFilterIndicator.setVisibility(isFilterActive ? View.VISIBLE : View.GONE);

        List<String> activeFilters = new ArrayList<>();

        if (!searchQuery.isEmpty()) {
            activeFilters.add("Recherche: \"" + searchQuery + "\"");
        }
        if (startDate != 0 && endDate != 0) {
            activeFilters.add("Du " + dateFormat.format(new Date(startDate)) +
                    " au " + dateFormat.format(new Date(endDate)));
        }
        if (!selectedCategoryIds.isEmpty()) {
            activeFilters.add(selectedCategoryIds.size() + " catégorie(s)");
        }

        if (!activeFilters.isEmpty()) {
            tvActiveFilters.setText(String.join(" • ", activeFilters));
            tvActiveFilters.setVisibility(View.VISIBLE);
        } else {
            tvActiveFilters.setVisibility(View.GONE);
        }
    }

    private double calculateTotal(List<Expense> expenses) {
        double total = 0;
        for (Expense expense : expenses) {
            total += expense.amount;
        }
        return total;
    }

    // ==========================================
    // C'EST ICI QUE LA CORRECTION A ÉTÉ FAITE !
    // ==========================================
    private String formatCurrency(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.getDefault());
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);

        // On utilise exactement le même nom "UserPrefs" et la même clé "currency_symbol"
        // que dans RegisterActivity et LoginActivity.
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String currency = prefs.getString("currency_symbol", "DH");

        return formatter.format(amount) + " " + currency;
    }
    // ==========================================

    private void setupFab() {
        ImageButton btnAdd = findViewById(R.id.btn_add);
        btnAdd.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddExpenseActivity.class);
            startActivity(intent);
        });
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    return true;
                }
                else if (id == R.id.nav_stats) {
                    startActivity(new Intent(MainActivity.this, StatsActivity.class));
                    return true;
                }
                else if (id == R.id.nav_categories) {
                    startActivity(new Intent(MainActivity.this, CategoryActivity.class));
                    return true;
                }
                else if (id == R.id.nav_profile) {
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                    return true;
                }

                return false;
            }
        });

        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void showDeleteConfirmationDialog(Expense expense) {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Supprimer la dépense ?")
                .setMessage("Voulez-vous vraiment supprimer \"" + expense.description + "\" ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    mExpenseViewModel.deleteExpense(expense);
                    Toast.makeText(MainActivity.this, "Dépense supprimée", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Déconnexion")
                .setMessage("Voulez-vous vraiment vous déconnecter ?")
                .setPositiveButton("Déconnecter", (dialog, which) -> {
                    mAuth.signOut();

                    // Nettoie bien "UserPrefs"
                    getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();

                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();

                    Toast.makeText(this, "Déconnecté avec succès", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (bottomNavigation.getSelectedItemId() != R.id.nav_home) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}