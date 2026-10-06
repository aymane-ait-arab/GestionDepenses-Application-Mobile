package com.example.gestiondepenses.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ExpenseRepository {

    private final CollectionReference expensesRef;
    private final CollectionReference categoriesRef;

    private final MutableLiveData<List<Expense>> allExpenses = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> allCategories = new MutableLiveData<>();

    public ExpenseRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Chemins : users/{id}/expenses ET users/{id}/categories
        expensesRef = db.collection("users").document(userId).collection("expenses");
        categoriesRef = db.collection("users").document(userId).collection("categories");

        // 1. Écouter les DEPENSES en temps réel
        expensesRef.orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        List<Expense> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            Expense e = doc.toObject(Expense.class);
                            e.setId(doc.getId()); // On force l'ID du document
                            list.add(e);
                        }
                        allExpenses.setValue(list);
                    }
                });

        // 2. Écouter les CATEGORIES en temps réel
        categoriesRef.orderBy("name")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        List<Category> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            Category c = doc.toObject(Category.class);
                            c.id = doc.getId(); // On force l'ID du document
                            list.add(c);
                        }
                        allCategories.setValue(list);
                    }
                });
    }

    // Getters LiveData
    public MutableLiveData<List<Expense>> getAllExpenses() { return allExpenses; }
    public MutableLiveData<List<Category>> getAllCategories() { return allCategories; }

    // CRUD Expense
    public void insertExpense(Expense expense) { expensesRef.add(expense); }
    public void deleteExpense(Expense expense) { if(expense.id != null) expensesRef.document(expense.id).delete(); }
    public void updateExpense(Expense expense) { if(expense.id != null) expensesRef.document(expense.id).set(expense); }

    // CRUD Category (Optionnel, utile pour l'init)
    public void insertCategory(Category category) { categoriesRef.add(category); }
}