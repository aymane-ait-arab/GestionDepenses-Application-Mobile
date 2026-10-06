package com.example.gestiondepenses.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.example.gestiondepenses.viewmodel.CategoryViewModel;
import com.example.gestiondepenses.viewmodel.ExpenseViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ExpenseViewModel expenseViewModel;
    private CategoryViewModel categoryViewModel; // Ajout du ViewModel des catégories
    private List<Expense> expenseList;
    private Map<String, String> categoryMap = new HashMap<>(); // Dictionnaire pour lier ID -> Nom de catégorie

    private TextView tvUserName, tvUserEmail, tvName, tvEmail, tvCountry, tvCurrency;
    private LinearLayout btnExportPdf, btnExportCsv;
    private MaterialButton btnLogout;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);
        categoryViewModel = new ViewModelProvider(this).get(CategoryViewModel.class); // Initialisation

        initializeViews();
        setupToolbar();
        loadUserData();
        setupClickListeners();
        loadExpenses();
        loadCategories(); // On charge les catégories au démarrage
    }

    private void initializeViews() {
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserEmail = findViewById(R.id.tv_user_email);
        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvCountry = findViewById(R.id.tv_country);
        tvCurrency = findViewById(R.id.tv_currency);
        btnExportPdf = findViewById(R.id.btn_export_pdf);
        btnExportCsv = findViewById(R.id.btn_export_csv);
        btnLogout = findViewById(R.id.btn_logout);
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

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        if (user != null) {
            String email = user.getEmail();
            String name = prefs.getString("user_name", "Utilisateur");
            String country = prefs.getString("user_country", "Maroc");
            String currency = prefs.getString("currency_symbol", "DH");

            tvUserName.setText(name);
            tvUserEmail.setText(email);
            tvName.setText(name);
            tvEmail.setText(email);
            tvCountry.setText(country);
            tvCurrency.setText(currency);
        }
    }

    private void loadExpenses() {
        expenseViewModel.getAllExpenses().observe(this, expenses -> {
            expenseList = expenses;
        });
    }

    // Chargement des catégories et remplissage du dictionnaire
    private void loadCategories() {
        categoryViewModel.getAllCategories().observe(this, categories -> {
            categoryMap.clear();
            if (categories != null) {
                for (Category category : categories) {
                    // Accès direct aux variables car elles sont 'public' dans ton modèle Category
                    if (category.id != null && category.name != null) {
                        categoryMap.put(category.id, category.name);
                    }
                }
            }
        });
    }

    private void setupClickListeners() {
        btnExportPdf.setOnClickListener(v -> exportToPdf());
        btnExportCsv.setOnClickListener(v -> exportToCsv());
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    // ============================================
    // PDF EXPORT
    // ============================================
    private void exportToPdf() {
        if (expenseList == null || expenseList.isEmpty()) {
            Toast.makeText(this, "Aucune dépense à exporter", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create file
            String fileName = "depenses_" + System.currentTimeMillis() + ".pdf";
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);

            // Initialize PDF
            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Title
            document.add(new Paragraph("Rapport des Dépenses")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            document.add(new Paragraph("Généré le: " + dateFormat.format(new Date()))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10));

            document.add(new Paragraph("\n"));

            // User info
            document.add(new Paragraph("Utilisateur: " + tvName.getText().toString()));
            document.add(new Paragraph("Email: " + tvEmail.getText().toString()));
            document.add(new Paragraph("Devise: " + tvCurrency.getText().toString()));
            document.add(new Paragraph("\n"));

            // Table
            float[] columnWidths = {1, 3, 2, 2};
            Table table = new Table(UnitValue.createPercentArray(columnWidths));
            table.setWidth(UnitValue.createPercentValue(100));

            // Header
            table.addHeaderCell(new Cell().add(new Paragraph("Date").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Catégorie").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Montant").setBold()));

            // Data
            double total = 0;
            for (Expense expense : expenseList) {
                // Accès direct aux variables public
                table.addCell(new Cell().add(new Paragraph(dateFormat.format(new Date(expense.date)))));

                String description = (expense.description != null) ? expense.description : "";
                table.addCell(new Cell().add(new Paragraph(description)));

                // Récupération du vrai nom de la catégorie !
                table.addCell(new Cell().add(new Paragraph(getCategoryName(expense.categoryId))));

                table.addCell(new Cell().add(new Paragraph(String.format(Locale.getDefault(), "%.2f %s",
                        expense.amount, tvCurrency.getText().toString()))));

                total += expense.amount;
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // Total
            document.add(new Paragraph("TOTAL: " + String.format(Locale.getDefault(), "%.2f %s",
                    total, tvCurrency.getText().toString()))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setBold()
                    .setFontSize(14));

            document.close();

            // Share file
            shareFile(file, "application/pdf");
            Toast.makeText(this, "PDF exporté: " + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ============================================
    // CSV EXPORT
    // ============================================
    private void exportToCsv() {
        if (expenseList == null || expenseList.isEmpty()) {
            Toast.makeText(this, "Aucune dépense à exporter", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = "depenses_" + System.currentTimeMillis() + ".csv";
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);

            FileWriter writer = new FileWriter(file);

            // Header
            writer.append("Date,Description,Categorie,Montant\n");

            // Data
            for (Expense expense : expenseList) {
                // Accès direct aux variables public
                String desc = (expense.description != null) ? expense.description.replace("\"", "\"\"") : "";

                writer.append(String.format(Locale.getDefault(), "%s,\"%s\",\"%s\",%.2f\n",
                        dateFormat.format(new Date(expense.date)),
                        desc,
                        getCategoryName(expense.categoryId), // Récupération du vrai nom
                        expense.amount));
            }

            writer.flush();
            writer.close();

            shareFile(file, "text/csv");
            Toast.makeText(this, "CSV exporté: " + fileName, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Méthode pour obtenir le nom à partir du dictionnaire
    private String getCategoryName(String categoryId) {
        if (categoryId != null && categoryMap.containsKey(categoryId)) {
            return categoryMap.get(categoryId);
        }
        return "Inconnue"; // Fallback si la catégorie a été supprimée ou n'existe pas
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(Intent.createChooser(intent, "Partager via"));
    }

    // ============================================
    // LOGOUT
    // ============================================
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Déconnexion")
                .setMessage("Voulez-vous vraiment vous déconnecter ?")
                .setPositiveButton("Déconnecter", (dialog, which) -> {
                    mAuth.signOut();
                    getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();

                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();

                    Toast.makeText(this, "Déconnecté avec succès", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}