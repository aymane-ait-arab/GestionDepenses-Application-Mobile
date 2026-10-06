package com.example.gestiondepenses.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gestiondepenses.MainActivity;
import com.example.gestiondepenses.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Vérifier si déjà connecté
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null){
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        etEmail = findViewById(R.id.et_email_login);
        etPassword = findViewById(R.id.et_password_login);
        Button btnLogin = findViewById(R.id.btn_login);
        TextView tvRegisterLink = findViewById(R.id.tv_register_link);

        btnLogin.setOnClickListener(v -> loginUser());

        tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();

                        // ON RÉCUPÈRE TOUTES LES INFOS DEPUIS FIREBASE
                        FirebaseFirestore.getInstance().collection("users").document(userId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        // CORRECTION MAJEURE ICI :
                                        // On utilise les VRAIS noms de champs de ta base de données (nom, pays, devise)

                                        String devise = documentSnapshot.getString("devise");
                                        if (devise == null) devise = "DH";

                                        String name = documentSnapshot.getString("nom");
                                        if (name == null) name = "Utilisateur";

                                        String country = documentSnapshot.getString("pays");
                                        if (country == null) country = "Maroc";

                                        // 2. On sauvegarde TOUT en local pour l'utiliser dans le Profil
                                        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                                .edit()
                                                .putString("currency_symbol", devise)
                                                .putString("user_name", name)         // Ajout du nom !
                                                .putString("user_country", country)   // Ajout du pays !
                                                .apply();

                                        // On lance l'app
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        finish();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(LoginActivity.this, "Erreur chargement profil", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // Si la connexion échoue (mauvais mot de passe ou email)
                        String error = task.getException() != null ? task.getException().getMessage() : "Erreur inconnue";
                        Toast.makeText(LoginActivity.this, "Échec : " + error, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}