package com.example.gestiondepenses.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.gestiondepenses.data.model.Category;
import com.example.gestiondepenses.data.model.Expense;
import com.example.gestiondepenses.data.repository.CategoryRepository;
import com.example.gestiondepenses.data.repository.ExpenseRepository;

import java.util.List;

public class ExpenseViewModel extends AndroidViewModel {

    private final ExpenseRepository repository;
    private final CategoryRepository categoryRepository;

    private final LiveData<List<Expense>> allExpenses;
    private final LiveData<List<Category>> allCategories;

    public ExpenseViewModel(@NonNull Application application) {
        super(application);
        // Initialisation des repositories
        repository = new ExpenseRepository();
        categoryRepository = new CategoryRepository();

        // Récupération des données
        allExpenses = repository.getAllExpenses();
        allCategories = categoryRepository.getAllCategories();
    }

    // --- GESTION DES DÉPENSES ---

    public void insertExpense(Expense expense) {
        repository.insertExpense(expense);
    }

    public void updateExpense(Expense expense) {
        repository.updateExpense(expense);
    }

    public void deleteExpense(Expense expense) {
        repository.deleteExpense(expense);
    }

    public LiveData<List<Expense>> getAllExpenses() {
        return allExpenses;
    }

    // --- GESTION DES CATÉGORIES (Pour le Spinner) ---

    public androidx.lifecycle.LiveData<List<Category>> getAllCategories() {
        return categoryRepository.getAllCategories(); // or however you access categories
    }

}
