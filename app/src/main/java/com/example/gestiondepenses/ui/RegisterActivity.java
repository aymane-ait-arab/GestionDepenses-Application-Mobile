package com.example.gestiondepenses.ui; // Vérifie que c'est bien ".ui" si c'est dans le dossier ui
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gestiondepenses.MainActivity;
import com.example.gestiondepenses.R;
import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.User;
import com.example.gestiondepenses.ui.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, etName;
    private Spinner spinnerCountry; // Changement de nom
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        etName = findViewById(R.id.et_name); // Si tu as ajouté le nom
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        spinnerCountry = findViewById(R.id.spinner_country);
        Button btnRegister = findViewById(R.id.btn_register);

        setupCountrySpinner();

        btnRegister.setOnClickListener(v -> registerUser());

        findViewById(R.id.tv_login_link).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void setupCountrySpinner() {
        List<String> countries = new ArrayList<>();
        countries.add("Maroc");
        countries.add("France");
        countries.add("Algérie");
        countries.add("Tunisie");
        countries.add("Sénégal");
        countries.add("États-Unis");
        countries.add("Royaume-Uni");
        countries.add("Espagne");
        countries.add("Allemagne");
        countries.add("Italie");
        countries.add("Belgique");
        countries.add("Canada");
        countries.add("Égypte");
        countries.add("Côte d'Ivoire");
        countries.add("Pays-Bas");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, countries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountry.setAdapter(adapter);
    }

    // --- PAYS LES PLUS POPULAIRES → DEVISE ---
    private String getDeviseFromPays(String pays) {
        switch (pays) {
            case "Maroc": return "DH";
            case "France": return "€";
            case "Algérie": return "DA";
            case "Tunisie": return "DT";
            case "Sénégal": return "CFA";
            case "États-Unis": return "$";
            case "Royaume-Uni": return "£";
            case "Espagne": return "€";
            case "Allemagne": return "€";
            case "Italie": return "€";
            case "Belgique": return "€";
            case "Canada": return "$";
            case "Égypte": return "EGP";
            case "Côte d'Ivoire": return "CFA";
            case "Pays-Bas": return "€";
            default: return "DH";
        }
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String name = etName.getText().toString().trim();  // GET NAME
        String selectedCountry = spinnerCountry.getSelectedItem().toString();  // GET COUNTRY

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        String determinedCurrency = getDeviseFromPays(selectedCountry);

                        // 1. Save User to Firestore
                        User newUser = new User(userId, name, email, selectedCountry, determinedCurrency);
                        FirebaseFirestore.getInstance().collection("users").document(userId).set(newUser);

                        // 2. SAVE ALL USER PREFERENCES (FIXED - added name)
                        SharedPreferences.Editor editor = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit();
                        editor.putString("user_name", name);                    // ADD THIS
                        editor.putString("user_country", selectedCountry);      // ADD THIS
                        editor.putString("currency_symbol", determinedCurrency);
                        editor.apply();

                        // 3. Create default categories
                        createDefaultCategories(userId);

                        Toast.makeText(RegisterActivity.this, "Bienvenue !", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, "Erreur : " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserPreferences(String pays, String devise) {
        SharedPreferences sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("user_country", pays);       // On sauvegarde le pays
        editor.putString("currency_symbol", devise);  // On sauvegarde la devise (Important pour l'affichage)
        editor.apply();
    }



    private void createDefaultCategories(String userId) {
        CollectionReference catRef = FirebaseFirestore.getInstance()
                .collection("users").document(userId).collection("categories");

        catRef.add(new Category("Alimentation", "#FF5722", true));
        catRef.add(new Category("Transport", "#2196F3", true));
        catRef.add(new Category("Logement", "#4CAF50", true));
        catRef.add(new Category("Loisirs", "#E91E63", true));
        catRef.add(new Category("Santé", "#F44336", true));
    }
}


