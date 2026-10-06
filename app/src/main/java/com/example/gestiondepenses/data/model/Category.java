package com.example.gestiondepenses.data.model;

import com.google.firebase.firestore.Exclude;

public class Category {
    @Exclude
    public String id; // L'ID Firestore (String)

    public String name;
    public String colorHex;
    public boolean isDefault;

    public Category() { } // OBLIGATOIRE

    public Category(String name, String colorHex, boolean isDefault) {
        this.name = name;
        this.colorHex = colorHex;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }


}