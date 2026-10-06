package com.example.gestiondepenses.data.model;

import com.google.firebase.firestore.Exclude;

public class Expense {
    @Exclude
    public String id; // String (plus de int !)

    public double amount;
    public long date;
    public String description;
    public String categoryId; // Lien vers la catégorie (String)

    public Expense() { } // OBLIGATOIRE

    public Expense(double amount, long date, String description, String categoryId) {
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.categoryId = categoryId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}