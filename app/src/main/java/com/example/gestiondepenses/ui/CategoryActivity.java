package com.example.gestiondepenses.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.ui.adapter.CategoryAdapter;
import com.example.gestiondepenses.viewmodel.CategoryViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Random;

public class CategoryActivity extends AppCompatActivity {

    private CategoryViewModel categoryViewModel;
    private CategoryAdapter adapter;
    private RecyclerView recyclerView;
    private LinearLayout emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_categories); // or activity_categories
        } catch (Exception e) {
            Toast.makeText(this, "Layout Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            return;
        }

        // Initialize views
        initializeViews();

        // Setup toolbar with back button
        setupToolbar();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup ViewModel
        setupViewModel();

        // Setup FAB
        setupFab();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_view_categories);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Catégories");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setClipToPadding(false);

        adapter = new CategoryAdapter(new CategoryAdapter.OnCategoryClickListener() {
            @Override
            public void onDeleteClick(Category category) {
                showDeleteConfirmation(category);
            }

            @Override
            public void onEditClick(Category category) {
                showEditCategoryDialog(category);
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        try {
            categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);
            categoryViewModel.getAllCategories().observe(this, categories -> {
                if (categories != null) {
                    adapter.setCategories(categories);
                    // Show/hide empty state
                    if (categories.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Erreur chargement: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fab_add_category);
        if (fab != null) {
            fab.setOnClickListener(v -> showAddCategoryDialog());
        }
    }

    private void showDeleteConfirmation(Category category) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Supprimer la catégorie ?")
                .setMessage("Voulez-vous vraiment supprimer \"" + category.name + "\" ?\n\nLes dépenses associées ne seront pas supprimées.")
                .setIcon(R.drawable.ic_launcher_foreground) // Change to your warning icon
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    categoryViewModel.delete(category);
                    Toast.makeText(this, "Catégorie supprimée", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showAddCategoryDialog() {
        // Create custom layout for better styling
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 0);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Nom de la catégorie (ex: Voiture)");
        input.setTextSize(16);

        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Nouvelle Catégorie")
                .setView(layout)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String randomColor = getRandomColorHex();
                        Category newCat = new Category(name, randomColor, false);
                        categoryViewModel.insert(newCat);
                        Toast.makeText(this, "Catégorie ajoutée", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Le nom ne peut pas être vide", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();

        // Focus and show keyboard
        input.requestFocus();
    }

    private void showEditCategoryDialog(Category category) {
        // Prevent editing default categories
        if (category.isDefault) {
            Toast.makeText(this, "Les catégories par défaut ne peuvent pas être modifiées", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 0);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(category.name);
        input.setSelection(category.name.length());
        input.setHint("Nouveau nom");

        layout.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Modifier la catégorie")
                .setView(layout)
                .setPositiveButton("Sauvegarder", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(category.name)) {
                        category.name = newName;
                        categoryViewModel.update(category);
                        Toast.makeText(this, "Catégorie modifiée", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();

        input.requestFocus();
    }

    private String getRandomColorHex() {
        Random random = new Random();
        // Generate pleasant colors (avoid too dark or too light)
        int hue = random.nextInt(360);
        float saturation = 0.5f + random.nextFloat() * 0.3f; // 0.5 - 0.8
        float brightness = 0.4f + random.nextFloat() * 0.3f; // 0.4 - 0.7

        int color = Color.HSVToColor(new float[]{hue, saturation, brightness});
        return String.format("#%06X", (0xFFFFFF & color));
    }
}