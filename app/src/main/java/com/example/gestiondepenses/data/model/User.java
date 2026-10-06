package com.example.gestiondepenses.data.model;

public class User {
    public String id;
    public String nom;
    public String email;
    public String pays;
    public String devise;

    public User() { } // OBLIGATOIRE

    public User(String id, String nom, String email, String pays, String devise) {
        this.id = id;
        this.nom = nom;
        this.email = email;
        this.pays = pays;
        this.devise = devise;
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id;}

}