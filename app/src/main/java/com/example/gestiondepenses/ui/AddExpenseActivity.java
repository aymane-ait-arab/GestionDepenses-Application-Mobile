package com.example.gestiondepenses.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.example.gestiondepenses.utils.ExpenseAIAssistant;
import com.example.gestiondepenses.viewmodel.CategoryViewModel;
import com.example.gestiondepenses.viewmodel.ExpenseViewModel;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private ExpenseViewModel expenseViewModel;
    private CategoryViewModel categoryViewModel;
    private EditText editTextSmart;
    private Button buttonAnalyze, buttonSave;
    private TextInputEditText editAmount, editDescription, editDate;
    private Spinner spinnerCategory;

    private boolean isEditMode = false;
    private String existingId = null;
    private String targetCategoryId = null;
    private Calendar myCalendar = Calendar.getInstance();
    private List<Category> categoryList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private ExpenseAIAssistant aiAssistant;
    private String originalSmartText = "";
    private ExpenseAIAssistant.AnalysisResult lastAnalysisResult;
    private String pendingCategoryToSelect = null; // When user adds new category, select it when list updates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);
        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        initViews();
        setupToolbar();
        setupDatePicker();

        aiAssistant = ExpenseAIAssistant.getInstance(this);

        // Load categories and initialize AI
        loadCategories();

        SharedPreferences sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String currency = sharedPref.getString("currency_symbol", "DH");
        editAmount.setHint("Montant (" + currency + ")");

        checkIfEditMode();

        buttonAnalyze.setOnClickListener(v -> {
            String text = editTextSmart.getText().toString().trim();
            if (!text.isEmpty()) {
                originalSmartText = text;
                // Check if AI is available and warn user
                if (!aiAssistant.isGroqAvailable() && !aiAssistant.isGeminiAvailable()) {
                    Toast.makeText(this, "⚠️ Mode regex uniquement (pas de clé API)", Toast.LENGTH_LONG).show();
                }
                analyzeWithValidation(text);
            } else {
                Toast.makeText(this, "Écrivez quelque chose (ex: Sephora 200dh)", Toast.LENGTH_SHORT).show();
            }
        });

        buttonSave.setOnClickListener(v -> saveExpense());
    }

    private void loadCategories() {
        categoryViewModel.getAllCategories().observe(this, categories -> {
            categoryList = categories;
            aiAssistant.setAvailableCategories(categories);

            setupCategorySpinner();
        });
    }

    private void setupCategorySpinner() {
        List<String> names = new ArrayList<>();
        for (Category c : categoryList) names.add(c.name);

        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(spinnerAdapter);

        if (isEditMode && targetCategoryId != null) {
            for (int i = 0; i < categoryList.size(); i++) {
                if (categoryList.get(i).id.equals(targetCategoryId)) {
                    spinnerCategory.setSelection(i);
                    break;
                }
            }
        } else if (pendingCategoryToSelect != null) {
            selectCategoryByName(pendingCategoryToSelect);
            pendingCategoryToSelect = null;
        }
    }

    // ============================================
    // NEW: Smart Analysis with Category Validation
    // ============================================
    private void analyzeWithValidation(String text) {
        buttonAnalyze.setEnabled(false);
        buttonAnalyze.setText("Analyse...");


        

        String currency = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("currency_symbol", "DH");

        aiAssistant.analyzeWithValidation(text, currency, new ExpenseAIAssistant.CategoryCheckCallback() {
            @Override
            public void onCategoryMissing(String suggestedCategory, ExpenseAIAssistant.AnalysisResult partialResult) {
                // ALWAYS show dialog for missing categories
                runOnUiThread(() -> showAddCategoryDialog(suggestedCategory, partialResult));
            }

            @Override
            public void onAnalysisComplete(ExpenseAIAssistant.AnalysisResult result) {
                runOnUiThread(() -> {
                    buttonAnalyze.setEnabled(true);
                    buttonAnalyze.setText("Analyser");

                    lastAnalysisResult = result;
                    applyAnalysisResult(result);

                    String provider = result.aiProvider != null ? result.aiProvider : (result.isAIGenerated ? "IA" : "Local");
                    Toast.makeText(AddExpenseActivity.this,
                            String.format("Analysé (%s) - Confiance: %.0f%%",
                                    provider, result.confidence * 100),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAnalysisFailed(String error) {
                runOnUiThread(() -> {
                    buttonAnalyze.setEnabled(true);
                    buttonAnalyze.setText("Analyser");
                    Toast.makeText(AddExpenseActivity.this,
                            "Erreur: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });


    }

    private void showAddCategoryDialog(String suggestedCategory, ExpenseAIAssistant.AnalysisResult partialResult) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Créer la catégorie pour continuer");
        builder.setMessage("La catégorie \"" + suggestedCategory + "\" n'existe pas.\n\n" +
                "Vous devez l'ajouter pour enregistrer cette dépense (\"" + partialResult.description + "\").");

        builder.setPositiveButton("Ajouter", (dialog, which) -> {
            String color = aiAssistant.generateRandomColor();
            Category newCategory = new Category(suggestedCategory, color, false);
            categoryViewModel.insert(newCategory);

            pendingCategoryToSelect = suggestedCategory;
            applyPartialResult(partialResult);
            Toast.makeText(this, "Catégorie ajoutée: " + suggestedCategory, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Choisir existante", (dialog, which) -> {
            applyPartialResult(partialResult);
            Toast.makeText(this, "Veuillez sélectionner une catégorie manuellement", Toast.LENGTH_LONG).show();
        });

        builder.setNeutralButton("Annuler", null);
        builder.show();
    }

    private void applyAnalysisResult(ExpenseAIAssistant.AnalysisResult result) {
        if (result.amount > 0) {
            editAmount.setText(String.valueOf(result.amount));
        }

        if (result.description != null && !result.description.isEmpty()) {
            editDescription.setText(result.description);
        }

        if (result.category != null && result.categoryExistsInApp) {
            selectCategoryByName(result.category);
        }

        if (result.date != null) {
            handleDate(result.date);
        }
    }

    private void applyPartialResult(ExpenseAIAssistant.AnalysisResult result) {
        // Apply what we have, let user fill the rest
        if (result.amount > 0) editAmount.setText(String.valueOf(result.amount));
        if (result.description != null) editDescription.setText(result.description);
        if (result.date != null) handleDate(result.date);
        // Category selection left to user
    }

    private void handleDate(String dateString) {
        if (dateString == null) return;

        if ("today".equalsIgnoreCase(dateString) || "aujourd'hui".equalsIgnoreCase(dateString)) {
            myCalendar = Calendar.getInstance();
        } else if ("yesterday".equalsIgnoreCase(dateString) || "hier".equalsIgnoreCase(dateString)) {
            myCalendar = Calendar.getInstance();
            myCalendar.add(Calendar.DAY_OF_MONTH, -1);
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                java.util.Date parsedDate = sdf.parse(dateString);
                if (parsedDate != null) {
                    myCalendar.setTime(parsedDate);
                }
            } catch (Exception e) {
                myCalendar = Calendar.getInstance();
            }
        }
        updateLabel();
    }

    // ... rest of your existing methods (saveExpense, selectCategoryByName, etc.) ...

    private void selectCategoryByName(String categoryToFind) {
        if (spinnerAdapter == null || categoryToFind == null) return;

        String searchClean = removeAccents(categoryToFind.toLowerCase());

        for (int i = 0; i < spinnerAdapter.getCount(); i++) {
            String item = spinnerAdapter.getItem(i);
            if (item != null) {
                String itemClean = removeAccents(item.toLowerCase());

                if (itemClean.contains(searchClean) || searchClean.contains(itemClean)) {
                    spinnerCategory.setSelection(i);
                    return;
                }
            }
        }
    }

    private String removeAccents(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Include your other existing methods here...
    private void initViews() {
        editTextSmart = findViewById(R.id.edit_text_smart);
        buttonAnalyze = findViewById(R.id.button_analyze);
        editAmount = findViewById(R.id.edit_amount);
        editDescription = findViewById(R.id.edit_description);
        editDate = findViewById(R.id.edit_date);
        spinnerCategory = findViewById(R.id.spinner_category);
        buttonSave = findViewById(R.id.button_save);
        updateLabel();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupDatePicker() {
        DatePickerDialog.OnDateSetListener date = (view, year, month, day) -> {
            myCalendar.set(Calendar.YEAR, year);
            myCalendar.set(Calendar.MONTH, month);
            myCalendar.set(Calendar.DAY_OF_MONTH, day);
            updateLabel();
        };
        editDate.setOnClickListener(v -> new DatePickerDialog(this, date,
                myCalendar.get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                myCalendar.get(Calendar.DAY_OF_MONTH)).show());
    }

    private void updateLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        editDate.setText(sdf.format(myCalendar.getTime()));
    }

    private void checkIfEditMode() {
        Intent intent = getIntent();
        if (intent.hasExtra("EXTRA_ID")) {
            isEditMode = true;
            existingId = intent.getStringExtra("EXTRA_ID");
            editDescription.setText(intent.getStringExtra("EXTRA_DESC"));
            editAmount.setText(String.valueOf(intent.getDoubleExtra("EXTRA_AMOUNT", 0.0)));
            myCalendar.setTimeInMillis(intent.getLongExtra("EXTRA_DATE", System.currentTimeMillis()));
            targetCategoryId = intent.getStringExtra("EXTRA_CAT_ID");
            updateLabel();
            buttonSave.setText("Modifier");
        }
    }

    private void saveExpense() {
        String desc = editDescription.getText().toString().trim();
        String amountStr = editAmount.getText().toString().trim();

        if (desc.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Remplissez le montant et la description", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Montant invalide", Toast.LENGTH_SHORT).show();
            return;
        }

        long date = myCalendar.getTimeInMillis();

        if (categoryList.isEmpty() || spinnerCategory.getSelectedItemPosition() < 0) {
            Toast.makeText(this, "Veuillez sélectionner une catégorie", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPosition = spinnerCategory.getSelectedItemPosition();
        String catId = categoryList.get(selectedPosition).id;
        String catName = categoryList.get(selectedPosition).name;

        // Learn from user correction if category was changed
        if (!originalSmartText.isEmpty() && lastAnalysisResult != null) {
            if (!catName.equals(lastAnalysisResult.category)) {
                ExpenseAIAssistant.AnalysisResult corrected =
                        new ExpenseAIAssistant.AnalysisResult.Builder()
                                .amount(amount)
                                .currency(getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                        .getString("currency_symbol", "DH"))
                                .description(desc)
                                .category(catName)
                                .date(editDate.getText().toString())
                                .build();
                aiAssistant.learnFromCorrection(originalSmartText, corrected);
            }
        }

        Expense expense = new Expense(amount, date, desc, catId);
        if (isEditMode) {
            expense.setId(existingId);
            expenseViewModel.updateExpense(expense);
            Toast.makeText(this, "Dépense modifiée", Toast.LENGTH_SHORT).show();
        } else {
            expenseViewModel.insertExpense(expense);
            Toast.makeText(this, "Dépense ajoutée", Toast.LENGTH_SHORT).show();
        }
        finish();
    }




}