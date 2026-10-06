package com.example.gestiondepenses.data.repository;

import androidx.lifecycle.MutableLiveData;
import com.example.gestiondepenses.data.model.Category;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CategoryRepository {

    private final CollectionReference categoriesRef;
    private final MutableLiveData<List<Category>> allCategories = new MutableLiveData<>();

    public CategoryRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Chemin : users/{userId}/categories
        categoriesRef = db.collection("users").document(userId).collection("categories");

        // Écoute en temps réel
        categoriesRef.orderBy("name")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        List<Category> list = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            Category c = doc.toObject(Category.class);
                            c.id = doc.getId(); // Important : ID String
                            list.add(c);
                        }
                        allCategories.setValue(list);
                    }
                });
    }

    public MutableLiveData<List<Category>> getAllCategories() {
        return allCategories;
    }

    public void insert(Category category) {
        categoriesRef.add(category);
    }

    public void delete(Category category) {
        if (category.id != null) {
            categoriesRef.document(category.id).delete();
        }
    }

    // Optionnel : Modifier
    public void update(Category category) {
        if (category.id != null) {
            categoriesRef.document(category.id).set(category);
        }
    }
}