package com.example.gestiondepenses.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.gestiondepenses.BuildConfig;
import com.example.gestiondepenses.data.model.Category;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Full AI Smart Expense Helper with:
 * - Groq API (free, open Llama models) - primary
 * - Gemini fallback
 * - Improved date & category matching
 * - Add new category when AI suggests one not in app
 */
public class ExpenseAIAssistant {
    private static final String TAG = "ExpenseAIAssistant";
    private static final int AI_TIMEOUT_SECONDS = 5;  // Reduced from 15 for faster fallback
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    private static ExpenseAIAssistant instance;
    private final Context context;
    private final Gson gson;
    private final Random random;
    private final OkHttpClient okHttpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GenerativeModelFutures geminiModel;
    private boolean geminiAvailable = false;
    private boolean groqAvailable = false;

    private List<Category> availableCategories = new ArrayList<>();
    private Map<String, String> categoryKeywordMap = new HashMap<>();
    /** Keywords that suggest a NEW category (user must create it) - checked before generic keywords */
    private Map<String, String> suggestedCategoryKeywords = new HashMap<>();

    private SharedPreferences learningPrefs;

    public interface CategoryCheckCallback {
        void onCategoryMissing(String suggestedCategoryName, AnalysisResult partialResult);
        void onAnalysisComplete(AnalysisResult result);
        void onAnalysisFailed(String error);
    }

    public static class AnalysisResult {
        public double amount;
        public String currency;
        public String description;
        public String category;
        public String date;
        public double confidence;
        public boolean isAIGenerated;
        public boolean success;
        public String errorMessage;
        public String rawAIResponse;
        public boolean categoryExistsInApp;
        public String aiProvider; // "groq", "gemini", "regex"

        public AnalysisResult() {
            this.amount = 0;
            this.confidence = 0;
            this.isAIGenerated = false;
            this.success = false;
            this.categoryExistsInApp = false;
        }

        public static class Builder {
            private final AnalysisResult result = new AnalysisResult();

            public Builder amount(double amount) {
                result.amount = amount;
                return this;
            }

            public Builder currency(String currency) {
                result.currency = currency;
                return this;
            }

            public Builder description(String description) {
                result.description = description;
                return this;
            }

            public Builder category(String category) {
                result.category = category;
                return this;
            }

            public Builder date(String date) {
                result.date = date;
                return this;
            }

            public Builder confidence(double confidence) {
                result.confidence = confidence;
                return this;
            }

            public Builder isAIGenerated(boolean isAI) {
                result.isAIGenerated = isAI;
                return this;
            }

            public Builder success(boolean success) {
                result.success = success;
                return this;
            }

            public Builder categoryExistsInApp(boolean exists) {
                result.categoryExistsInApp = exists;
                return this;
            }

            public AnalysisResult build() {
                return result;
            }
        }
    }

    private ExpenseAIAssistant(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.random = new Random();
        this.learningPrefs = context.getSharedPreferences("AI_Learning", Context.MODE_PRIVATE);
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        initProviders();
        initCategoryKeywords();
    }

    public static synchronized ExpenseAIAssistant getInstance(Context context) {
        if (instance == null) {
            instance = new ExpenseAIAssistant(context);
        }
        return instance;
    }

    private void initProviders() {
        String groqKey = BuildConfig.GROQ_API_KEY;
        String geminiKey = BuildConfig.GEMINI_API_KEY;

        groqAvailable = groqKey != null && !groqKey.isEmpty();
        if (groqAvailable) {
            Log.d(TAG, "Groq API (free open model) available");
        }

        if (geminiKey != null && !geminiKey.isEmpty()) {
            try {
                GenerationConfig.Builder configBuilder = new GenerationConfig.Builder();
                configBuilder.responseMimeType = "application/json";
                configBuilder.temperature = 0.1f;
                configBuilder.topK = 1;
                configBuilder.topP = 0.1f;
                configBuilder.maxOutputTokens = 500;

                GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", geminiKey, configBuilder.build());
                this.geminiModel = GenerativeModelFutures.from(gm);
                this.geminiAvailable = true;
                Log.d(TAG, "Gemini API available");
            } catch (Exception e) {
                Log.e(TAG, "Gemini init failed: " + e.getMessage());
            }
        }
    }

    public void setAvailableCategories(List<Category> categories) {
        this.availableCategories = new ArrayList<>(categories);
        buildCategoryKeywordMap();
        Log.d(TAG, "Loaded " + categories.size() + " categories");
    }

    private void initCategoryKeywords() {
        // ALIMENTATION - Food & Dining (expanded)
        categoryKeywordMap.put("food", "Alimentation");
        categoryKeywordMap.put("eat", "Alimentation");
        categoryKeywordMap.put("restaurant", "Alimentation");
        categoryKeywordMap.put("mcdo", "Alimentation");
        categoryKeywordMap.put("burger", "Alimentation");
        categoryKeywordMap.put("pizza", "Alimentation");
        categoryKeywordMap.put("groceries", "Alimentation");
        categoryKeywordMap.put("supermarket", "Alimentation");
        categoryKeywordMap.put("aliment", "Alimentation");
        categoryKeywordMap.put("nourriture", "Alimentation");
        categoryKeywordMap.put("repas", "Alimentation");
        categoryKeywordMap.put("kfc", "Alimentation");
        categoryKeywordMap.put("tacos", "Alimentation");
        categoryKeywordMap.put("kebab", "Alimentation");
        categoryKeywordMap.put("sandwich", "Alimentation");
        categoryKeywordMap.put("croissant", "Alimentation");
        categoryKeywordMap.put("patisserie", "Alimentation");
        categoryKeywordMap.put("boulangerie", "Alimentation");
        categoryKeywordMap.put("pain", "Alimentation");
        categoryKeywordMap.put("baguette", "Alimentation");
        categoryKeywordMap.put("marche", "Alimentation");
        categoryKeywordMap.put("legume", "Alimentation");
        categoryKeywordMap.put("fruit", "Alimentation");
        categoryKeywordMap.put("viande", "Alimentation");
        categoryKeywordMap.put("poisson", "Alimentation");
        categoryKeywordMap.put("poulet", "Alimentation");
        categoryKeywordMap.put("cafe", "Alimentation");
        categoryKeywordMap.put("starbucks", "Alimentation");
        categoryKeywordMap.put("burgerking", "Alimentation");
        categoryKeywordMap.put("subway", "Alimentation");
        categoryKeywordMap.put("sushi", "Alimentation");
        categoryKeywordMap.put("chinois", "Alimentation");
        categoryKeywordMap.put("indien", "Alimentation");
        categoryKeywordMap.put("traiteur", "Alimentation");
        categoryKeywordMap.put("livraison", "Alimentation");
        categoryKeywordMap.put("deliveroo", "Alimentation");
        categoryKeywordMap.put("uber eats", "Alimentation");

        // TRANSPORT - Transportation (expanded)
        categoryKeywordMap.put("transport", "Transport");
        categoryKeywordMap.put("taxi", "Transport");
        categoryKeywordMap.put("uber", "Transport");
        categoryKeywordMap.put("bus", "Transport");
        categoryKeywordMap.put("train", "Transport");
        categoryKeywordMap.put("metro", "Transport");
        categoryKeywordMap.put("car", "Transport");
        categoryKeywordMap.put("essence", "Transport");
        categoryKeywordMap.put("fuel", "Transport");
        categoryKeywordMap.put("parking", "Transport");
        categoryKeywordMap.put("station", "Transport");
        categoryKeywordMap.put("tram", "Transport");
        categoryKeywordMap.put("bateau", "Transport");
        categoryKeywordMap.put("avion", "Transport");
        categoryKeywordMap.put("vol", "Transport");
        categoryKeywordMap.put("billet", "Transport");
        categoryKeywordMap.put("peage", "Transport");
        categoryKeywordMap.put("autoroute", "Transport");
        categoryKeywordMap.put("reparation", "Transport");
        categoryKeywordMap.put("garage", "Transport");
        categoryKeywordMap.put("mecanicien", "Transport");
        categoryKeywordMap.put("pneu", "Transport");
        categoryKeywordMap.put("vidange", "Transport");
        categoryKeywordMap.put("assurance voiture", "Transport");
        categoryKeywordMap.put("permis", "Transport");
        categoryKeywordMap.put("location", "Transport");
        categoryKeywordMap.put("hertz", "Transport");
        categoryKeywordMap.put("europcar", "Transport");
        categoryKeywordMap.put("sixt", "Transport");
        categoryKeywordMap.put("heetch", "Transport");
        categoryKeywordMap.put("bolt", "Transport");
        categoryKeywordMap.put("careem", "Transport");

        // LOGEMENT - Housing & Bills (expanded)
        categoryKeywordMap.put("home", "Logement");
        categoryKeywordMap.put("house", "Logement");
        categoryKeywordMap.put("rent", "Logement");
        categoryKeywordMap.put("loyer", "Logement");
        categoryKeywordMap.put("electric", "Logement");
        categoryKeywordMap.put("electricity", "Logement");
        categoryKeywordMap.put("water", "Logement");
        categoryKeywordMap.put("eau", "Logement");
        categoryKeywordMap.put("internet", "Logement");
        categoryKeywordMap.put("phone", "Logement");
        categoryKeywordMap.put("gaz", "Logement");
        categoryKeywordMap.put("electricite", "Logement");
        categoryKeywordMap.put("facture", "Logement");
        categoryKeywordMap.put("facture eau", "Logement");
        categoryKeywordMap.put("facture gaz", "Logement");
        categoryKeywordMap.put("orange", "Logement");
        categoryKeywordMap.put("maroc telecom", "Logement");
        categoryKeywordMap.put("inwi", "Logement");
        categoryKeywordMap.put("mobile", "Logement");
        categoryKeywordMap.put("telephone", "Logement");
        categoryKeywordMap.put("forfait", "Logement");
        categoryKeywordMap.put("recharge", "Logement");
        categoryKeywordMap.put("wifi", "Logement");
        categoryKeywordMap.put("adsl", "Logement");
        categoryKeywordMap.put("fiber", "Logement");
        categoryKeywordMap.put("fibere", "Logement");
        categoryKeywordMap.put("box", "Logement");
        categoryKeywordMap.put("chauffage", "Logement");
        categoryKeywordMap.put("climatisation", "Logement");
        categoryKeywordMap.put("menage", "Logement");
        categoryKeywordMap.put("conciergerie", "Logement");
        categoryKeywordMap.put("syndic", "Logement");
        categoryKeywordMap.put("copropriete", "Logement");
        categoryKeywordMap.put("assurance habitation", "Logement");
        categoryKeywordMap.put("meuble", "Logement");
        categoryKeywordMap.put("decoration", "Logement");
        categoryKeywordMap.put("bricolage", "Logement");
        categoryKeywordMap.put("jardin", "Logement");

        // LOISIRS - Entertainment & Leisure (expanded)
        categoryKeywordMap.put("fun", "Loisirs");
        categoryKeywordMap.put("game", "Loisirs");
        categoryKeywordMap.put("movie", "Loisirs");
        categoryKeywordMap.put("cinema", "Loisirs");
        categoryKeywordMap.put("sport", "Loisirs");
        categoryKeywordMap.put("gym", "Loisirs");
        categoryKeywordMap.put("music", "Loisirs");
        categoryKeywordMap.put("concert", "Loisirs");
        categoryKeywordMap.put("travel", "Loisirs");
        categoryKeywordMap.put("vacation", "Loisirs");
        categoryKeywordMap.put("loisir", "Loisirs");
        categoryKeywordMap.put("netflix", "Loisirs");
        categoryKeywordMap.put("spotify", "Loisirs");
        categoryKeywordMap.put("youtube", "Loisirs");
        categoryKeywordMap.put("disney", "Loisirs");
        categoryKeywordMap.put("amazon prime", "Loisirs");
        categoryKeywordMap.put("streaming", "Loisirs");
        categoryKeywordMap.put("abonnement", "Loisirs");
        categoryKeywordMap.put("theatre", "Loisirs");
        categoryKeywordMap.put("spectacle", "Loisirs");
        categoryKeywordMap.put("musee", "Loisirs");
        categoryKeywordMap.put("exposition", "Loisirs");
        categoryKeywordMap.put("parc", "Loisirs");
        categoryKeywordMap.put("attraction", "Loisirs");
        categoryKeywordMap.put("bowling", "Loisirs");
        categoryKeywordMap.put("billard", "Loisirs");
        categoryKeywordMap.put("karting", "Loisirs");
        categoryKeywordMap.put("laser game", "Loisirs");
        categoryKeywordMap.put("escape game", "Loisirs");
        categoryKeywordMap.put("casino", "Loisirs");
        categoryKeywordMap.put("bar", "Loisirs");
        categoryKeywordMap.put("nightclub", "Loisirs");
        categoryKeywordMap.put("boite", "Loisirs");
        categoryKeywordMap.put("discotheque", "Loisirs");
        categoryKeywordMap.put("shisha", "Loisirs");
        categoryKeywordMap.put("pub", "Loisirs");
        categoryKeywordMap.put("festival", "Loisirs");
        categoryKeywordMap.put("evenement", "Loisirs");
        categoryKeywordMap.put("camping", "Loisirs");
        categoryKeywordMap.put("hotel", "Loisirs");
        categoryKeywordMap.put("airbnb", "Loisirs");
        categoryKeywordMap.put("booking", "Loisirs");
        categoryKeywordMap.put("fitness", "Loisirs");
        categoryKeywordMap.put("piscine", "Loisirs");
        categoryKeywordMap.put("tennis", "Loisirs");
        categoryKeywordMap.put("football", "Loisirs");
        categoryKeywordMap.put("basket", "Loisirs");
        categoryKeywordMap.put("handball", "Loisirs");
        categoryKeywordMap.put("rugby", "Loisirs");
        categoryKeywordMap.put("natation", "Loisirs");
        categoryKeywordMap.put("yoga", "Loisirs");
        categoryKeywordMap.put("pilates", "Loisirs");
        categoryKeywordMap.put("musculation", "Loisirs");
        categoryKeywordMap.put("salle de sport", "Loisirs");
        categoryKeywordMap.put("basic fit", "Loisirs");
        categoryKeywordMap.put("neoness", "Loisirs");
        categoryKeywordMap.put("keepcool", "Loisirs");
        categoryKeywordMap.put("livre", "Loisirs");
        categoryKeywordMap.put("magazine", "Loisirs");
        categoryKeywordMap.put("journal", "Loisirs");
        categoryKeywordMap.put("fnac", "Loisirs");
        categoryKeywordMap.put("cd", "Loisirs");
        categoryKeywordMap.put("dvd", "Loisirs");
        categoryKeywordMap.put("blu-ray", "Loisirs");
        categoryKeywordMap.put("vinyl", "Loisirs");
        categoryKeywordMap.put("instrument", "Loisirs");
        categoryKeywordMap.put("guitare", "Loisirs");
        categoryKeywordMap.put("piano", "Loisirs");
        categoryKeywordMap.put("cours de musique", "Loisirs");

        // SANTE - Health & Medical (expanded)
        categoryKeywordMap.put("health", "Santé");
        categoryKeywordMap.put("doctor", "Santé");
        categoryKeywordMap.put("med", "Santé");
        categoryKeywordMap.put("medicine", "Santé");
        categoryKeywordMap.put("pharmacy", "Santé");
        categoryKeywordMap.put("hospital", "Santé");
        categoryKeywordMap.put("dental", "Santé");
        categoryKeywordMap.put("santé", "Santé");
        categoryKeywordMap.put("sante", "Santé");
        categoryKeywordMap.put("médecin", "Santé");
        categoryKeywordMap.put("pharmacie", "Santé");
        categoryKeywordMap.put("hopital", "Santé");
        categoryKeywordMap.put("hopitale", "Santé");
        categoryKeywordMap.put("clinique", "Santé");
        categoryKeywordMap.put("dentiste", "Santé");
        categoryKeywordMap.put("dent", "Santé");
        categoryKeywordMap.put("opticien", "Santé");
        categoryKeywordMap.put("lunettes", "Santé");
        categoryKeywordMap.put("lentilles", "Santé");
        categoryKeywordMap.put("ordonnance", "Santé");
        categoryKeywordMap.put("medicament", "Santé");
        categoryKeywordMap.put("pilule", "Santé");
        categoryKeywordMap.put("vaccin", "Santé");
        categoryKeywordMap.put("analyse", "Santé");
        categoryKeywordMap.put("prise de sang", "Santé");
        categoryKeywordMap.put("radio", "Santé");
        categoryKeywordMap.put("scanner", "Santé");
        categoryKeywordMap.put("irm", "Santé");
        categoryKeywordMap.put("echographie", "Santé");
        categoryKeywordMap.put("consultation", "Santé");
        categoryKeywordMap.put("visite", "Santé");
        categoryKeywordMap.put("infirmier", "Santé");
        categoryKeywordMap.put("kiné", "Santé");
        categoryKeywordMap.put("kinesitherapeute", "Santé");
        categoryKeywordMap.put("physiotherapeute", "Santé");
        categoryKeywordMap.put("osteopathe", "Santé");
        categoryKeywordMap.put("chiropracteur", "Santé");
        categoryKeywordMap.put("acupuncture", "Santé");
        categoryKeywordMap.put("psychologue", "Santé");
        categoryKeywordMap.put("psychiatre", "Santé");
        categoryKeywordMap.put("therapie", "Santé");
        categoryKeywordMap.put("mutuelle", "Santé");
        categoryKeywordMap.put("assurance sante", "Santé");
        categoryKeywordMap.put("chirurgie", "Santé");
        categoryKeywordMap.put("operation", "Santé");
        categoryKeywordMap.put("urgence", "Santé");
        categoryKeywordMap.put("samu", "Santé");
        categoryKeywordMap.put("pompiers", "Santé");
        categoryKeywordMap.put("ambulance", "Santé");
        categoryKeywordMap.put("optical center", "Santé");
        categoryKeywordMap.put("grand optical", "Santé");
        categoryKeywordMap.put("afflelou", "Santé");
        categoryKeywordMap.put("krys", "Santé");

        // Suggested NEW categories - user must create to continue (checked first, any category type)
        suggestedCategoryKeywords.put("gaming", "Gaming");
        suggestedCategoryKeywords.put("gamer", "Gaming");
        suggestedCategoryKeywords.put("souri", "Gaming");
        suggestedCategoryKeywords.put("mouse", "Gaming");
        suggestedCategoryKeywords.put("clavier", "Gaming");
        suggestedCategoryKeywords.put("keyboard", "Gaming");
        suggestedCategoryKeywords.put("jeu", "Gaming");
        suggestedCategoryKeywords.put("jeux", "Gaming");
        suggestedCategoryKeywords.put("playstation", "Gaming");
        suggestedCategoryKeywords.put("xbox", "Gaming");
        suggestedCategoryKeywords.put("nintendo", "Gaming");
        suggestedCategoryKeywords.put("steam", "Gaming");
        suggestedCategoryKeywords.put("epic games", "Gaming");
        suggestedCategoryKeywords.put("origin", "Gaming");
        suggestedCategoryKeywords.put("battle.net", "Gaming");
        suggestedCategoryKeywords.put("manette", "Gaming");
        suggestedCategoryKeywords.put("casque", "Gaming");
        suggestedCategoryKeywords.put("ecran", "Gaming");
        suggestedCategoryKeywords.put("pc gamer", "Gaming");
        suggestedCategoryKeywords.put("config", "Gaming");
        suggestedCategoryKeywords.put("carte graphique", "Gaming");
        suggestedCategoryKeywords.put("processeur", "Gaming");
        suggestedCategoryKeywords.put("ram", "Gaming");
        suggestedCategoryKeywords.put("ssd", "Gaming");

        suggestedCategoryKeywords.put("electronique", "Électronique");
        suggestedCategoryKeywords.put("electronics", "Électronique");
        suggestedCategoryKeywords.put("telephone", "Électronique");
        suggestedCategoryKeywords.put("smartphone", "Électronique");
        suggestedCategoryKeywords.put("iphone", "Électronique");
        suggestedCategoryKeywords.put("samsung", "Électronique");
        suggestedCategoryKeywords.put("xiaomi", "Électronique");
        suggestedCategoryKeywords.put("huawei", "Électronique");
        suggestedCategoryKeywords.put("oppo", "Électronique");
        suggestedCategoryKeywords.put("tablette", "Électronique");
        suggestedCategoryKeywords.put("ipad", "Électronique");
        suggestedCategoryKeywords.put("macbook", "Électronique");
        suggestedCategoryKeywords.put("laptop", "Électronique");
        suggestedCategoryKeywords.put("ordinateur", "Électronique");
        suggestedCategoryKeywords.put("pc", "Électronique");
        suggestedCategoryKeywords.put("portable", "Électronique");
        suggestedCategoryKeywords.put("fixe", "Électronique");
        suggestedCategoryKeywords.put("ecran pc", "Électronique");
        suggestedCategoryKeywords.put("imprimante", "Électronique");
        suggestedCategoryKeywords.put("camera", "Électronique");
        suggestedCategoryKeywords.put("appareil photo", "Électronique");
        suggestedCategoryKeywords.put("gopro", "Électronique");
        suggestedCategoryKeywords.put("drone", "Électronique");
        suggestedCategoryKeywords.put("console", "Électronique");
        suggestedCategoryKeywords.put("tv", "Électronique");
        suggestedCategoryKeywords.put("tele", "Électronique");
        suggestedCategoryKeywords.put("television", "Électronique");
        suggestedCategoryKeywords.put("smart tv", "Électronique");
        suggestedCategoryKeywords.put("enceinte", "Électronique");
        suggestedCategoryKeywords.put("enceinte bluetooth", "Électronique");
        suggestedCategoryKeywords.put("bose", "Électronique");
        suggestedCategoryKeywords.put("sony", "Électronique");
        suggestedCategoryKeywords.put("jbl", "Électronique");
        suggestedCategoryKeywords.put("marshall", "Électronique");
        suggestedCategoryKeywords.put("apple", "Électronique");
        suggestedCategoryKeywords.put("apple store", "Électronique");
        suggestedCategoryKeywords.put("darty", "Électronique");
        suggestedCategoryKeywords.put("boulanger", "Électronique");
        suggestedCategoryKeywords.put("fnac", "Électronique");
        suggestedCategoryKeywords.put("micromania", "Électronique");

        suggestedCategoryKeywords.put("vet", "Vétérinaire");
        suggestedCategoryKeywords.put("veterinaire", "Vétérinaire");
        suggestedCategoryKeywords.put("veto", "Vétérinaire");
        suggestedCategoryKeywords.put("vetérinaire", "Vétérinaire");
        suggestedCategoryKeywords.put("chat", "Vétérinaire");
        suggestedCategoryKeywords.put("chien", "Vétérinaire");
        suggestedCategoryKeywords.put("animal", "Vétérinaire");
        suggestedCategoryKeywords.put("animaux", "Vétérinaire");
        suggestedCategoryKeywords.put("compagnie", "Vétérinaire");
        suggestedCategoryKeywords.put("pet", "Vétérinaire");
        suggestedCategoryKeywords.put("croquette", "Vétérinaire");
        suggestedCategoryKeywords.put("nourriture chat", "Vétérinaire");
        suggestedCategoryKeywords.put("nourriture chien", "Vétérinaire");
        suggestedCategoryKeywords.put("litiere", "Vétérinaire");
        suggestedCategoryKeywords.put("panier", "Vétérinaire");
        suggestedCategoryKeywords.put("laisse", "Vétérinaire");
        suggestedCategoryKeywords.put("collier", "Vétérinaire");
        suggestedCategoryKeywords.put("jouet chat", "Vétérinaire");
        suggestedCategoryKeywords.put("jouet chien", "Vétérinaire");
        suggestedCategoryKeywords.put("griffoir", "Vétérinaire");
        suggestedCategoryKeywords.put("arbre a chat", "Vétérinaire");
        suggestedCategoryKeywords.put("toilettage", "Vétérinaire");
        suggestedCategoryKeywords.put("pension", "Vétérinaire");
        suggestedCategoryKeywords.put("garde animaux", "Vétérinaire");

        // NEW: Education category
        suggestedCategoryKeywords.put("education", "Éducation");
        suggestedCategoryKeywords.put("ecole", "Éducation");
        suggestedCategoryKeywords.put("ecolage", "Éducation");
        suggestedCategoryKeywords.put("scolarite", "Éducation");
        suggestedCategoryKeywords.put("universite", "Éducation");
        suggestedCategoryKeywords.put("fac", "Éducation");
        suggestedCategoryKeywords.put("faculte", "Éducation");
        suggestedCategoryKeywords.put("formation", "Éducation");
        suggestedCategoryKeywords.put("cours", "Éducation");
        suggestedCategoryKeywords.put("professeur", "Éducation");
        suggestedCategoryKeywords.put("soutien", "Éducation");
        suggestedCategoryKeywords.put("particulier", "Éducation");
        suggestedCategoryKeywords.put("langue", "Éducation");
        suggestedCategoryKeywords.put("anglais", "Éducation");
        suggestedCategoryKeywords.put("francais", "Éducation");
        suggestedCategoryKeywords.put("espagnol", "Éducation");
        suggestedCategoryKeywords.put("allemand", "Éducation");
        suggestedCategoryKeywords.put("certificat", "Éducation");
        suggestedCategoryKeywords.put("diplome", "Éducation");
        suggestedCategoryKeywords.put("examen", "Éducation");
        suggestedCategoryKeywords.put("concours", "Éducation");
        suggestedCategoryKeywords.put("livre scolaire", "Éducation");
        suggestedCategoryKeywords.put("fourniture", "Éducation");
        suggestedCategoryKeywords.put("cartable", "Éducation");
        suggestedCategoryKeywords.put("cahier", "Éducation");
        suggestedCategoryKeywords.put("stylo", "Éducation");
        suggestedCategoryKeywords.put("calculatrice", "Éducation");

        // NEW: Beauté & Bien-être
        suggestedCategoryKeywords.put("beauté", "Beauté");
        suggestedCategoryKeywords.put("beaute", "Beauté");
        suggestedCategoryKeywords.put("coiffeur", "Beauté");
        suggestedCategoryKeywords.put("coiffure", "Beauté");
        suggestedCategoryKeywords.put("salon", "Beauté");
        suggestedCategoryKeywords.put("barbier", "Beauté");
        suggestedCategoryKeywords.put("rasage", "Beauté");
        suggestedCategoryKeywords.put("coupe", "Beauté");
        suggestedCategoryKeywords.put("coloration", "Beauté");
        suggestedCategoryKeywords.put("manucure", "Beauté");
        suggestedCategoryKeywords.put("pedicure", "Beauté");
        suggestedCategoryKeywords.put("ongle", "Beauté");
        suggestedCategoryKeywords.put("vernis", "Beauté");
        suggestedCategoryKeywords.put("spa", "Beauté");
        suggestedCategoryKeywords.put("massage", "Beauté");
        suggestedCategoryKeywords.put("hammam", "Beauté");
        suggestedCategoryKeywords.put("sauna", "Beauté");
        suggestedCategoryKeywords.put("soin", "Beauté");
        suggestedCategoryKeywords.put("visage", "Beauté");
        suggestedCategoryKeywords.put("creme", "Beauté");
        suggestedCategoryKeywords.put("maquillage", "Beauté");
        suggestedCategoryKeywords.put("parfum", "Beauté");
        suggestedCategoryKeywords.put("sephora", "Beauté");
        suggestedCategoryKeywords.put("marionnaud", "Beauté");
        suggestedCategoryKeywords.put("nocibe", "Beauté");
        suggestedCategoryKeywords.put("yves rocher", "Beauté");
        suggestedCategoryKeywords.put("the body shop", "Beauté");
        suggestedCategoryKeywords.put("kiko", "Beauté");
        suggestedCategoryKeywords.put("mac", "Beauté");
        suggestedCategoryKeywords.put("nyx", "Beauté");
        suggestedCategoryKeywords.put("l'oreal", "Beauté");
        suggestedCategoryKeywords.put("nivea", "Beauté");
        suggestedCategoryKeywords.put("garnier", "Beauté");
        suggestedCategoryKeywords.put("dove", "Beauté");
        suggestedCategoryKeywords.put("lotion", "Beauté");
        suggestedCategoryKeywords.put("shampoing", "Beauté");
        suggestedCategoryKeywords.put("apres-shampoing", "Beauté");
        suggestedCategoryKeywords.put("gel douche", "Beauté");
        suggestedCategoryKeywords.put("deodorant", "Beauté");
        suggestedCategoryKeywords.put("rasoir", "Beauté");
        suggestedCategoryKeywords.put("brosse a dent", "Beauté");
        suggestedCategoryKeywords.put("dentifrice", "Beauté");
        suggestedCategoryKeywords.put("protege slip", "Beauté");
        suggestedCategoryKeywords.put("serviette", "Beauté");
        suggestedCategoryKeywords.put("tampon", "Beauté");
        suggestedCategoryKeywords.put("couche", "Beauté");
        suggestedCategoryKeywords.put("pommade", "Beauté");

        // NEW: Vêtements & Shopping
        suggestedCategoryKeywords.put("vetement", "Vêtements");
        suggestedCategoryKeywords.put("habit", "Vêtements");
        suggestedCategoryKeywords.put("chaussure", "Vêtements");
        suggestedCategoryKeywords.put("sac", "Vêtements");
        suggestedCategoryKeywords.put("montre", "Vêtements");
        suggestedCategoryKeywords.put("bijou", "Vêtements");
        suggestedCategoryKeywords.put("bijouterie", "Vêtements");
        suggestedCategoryKeywords.put("zara", "Vêtements");
        suggestedCategoryKeywords.put("h&m", "Vêtements");
        suggestedCategoryKeywords.put("bershka", "Vêtements");
        suggestedCategoryKeywords.put("pull&bear", "Vêtements");
        suggestedCategoryKeywords.put("mango", "Vêtements");
        suggestedCategoryKeywords.put("stradivarius", "Vêtements");
        suggestedCategoryKeywords.put("massimo dutti", "Vêtements");
        suggestedCategoryKeywords.put("oysho", "Vêtements");
        suggestedCategoryKeywords.put("urban outfitters", "Vêtements");
        suggestedCategoryKeywords.put("uniqlo", "Vêtements");
        suggestedCategoryKeywords.put("decathlon", "Vêtements");
        suggestedCategoryKeywords.put("nike", "Vêtements");
        suggestedCategoryKeywords.put("adidas", "Vêtements");
        suggestedCategoryKeywords.put("puma", "Vêtements");
        suggestedCategoryKeywords.put("reebok", "Vêtements");
        suggestedCategoryKeywords.put("new balance", "Vêtements");
        suggestedCategoryKeywords.put("asics", "Vêtements");
        suggestedCategoryKeywords.put("converse", "Vêtements");
        suggestedCategoryKeywords.put("vans", "Vêtements");
        suggestedCategoryKeywords.put("lacoste", "Vêtements");
        suggestedCategoryKeywords.put("ralph lauren", "Vêtements");
        suggestedCategoryKeywords.put("tommy hilfiger", "Vêtements");
        suggestedCategoryKeywords.put("levis", "Vêtements");
        suggestedCategoryKeywords.put("diesel", "Vêtements");
        suggestedCategoryKeywords.put("calvin klein", "Vêtements");
        suggestedCategoryKeywords.put("guess", "Vêtements");
        suggestedCategoryKeywords.put("michael kors", "Vêtements");
        suggestedCategoryKeywords.put("louis vuitton", "Vêtements");
        suggestedCategoryKeywords.put("gucci", "Vêtements");
        suggestedCategoryKeywords.put("prada", "Vêtements");
        suggestedCategoryKeywords.put("chanel", "Vêtements");
        suggestedCategoryKeywords.put("dior", "Vêtements");
        suggestedCategoryKeywords.put("cartier", "Vêtements");
        suggestedCategoryKeywords.put("tiffany", "Vêtements");
        suggestedCategoryKeywords.put("swarovski", "Vêtements");
        suggestedCategoryKeywords.put("pandora", "Vêtements");
        suggestedCategoryKeywords.put("cluse", "Vêtements");
        suggestedCategoryKeywords.put("daniel wellington", "Vêtements");
        suggestedCategoryKeywords.put("rolex", "Vêtements");
        suggestedCategoryKeywords.put("omega", "Vêtements");
        suggestedCategoryKeywords.put("tag heuer", "Vêtements");
        suggestedCategoryKeywords.put("tailleur", "Vêtements");
        suggestedCategoryKeywords.put("retouche", "Vêtements");
        suggestedCategoryKeywords.put("pressing", "Vêtements");

        // NEW: Cadeaux & Dons
        suggestedCategoryKeywords.put("cadeau", "Cadeaux");
        suggestedCategoryKeywords.put("cadeaux", "Cadeaux");
        suggestedCategoryKeywords.put("anniversaire", "Cadeaux");
        suggestedCategoryKeywords.put("mariage", "Cadeaux");
        suggestedCategoryKeywords.put("naissance", "Cadeaux");
        suggestedCategoryKeywords.put("bapteme", "Cadeaux");
        suggestedCategoryKeywords.put("communion", "Cadeaux");
        suggestedCategoryKeywords.put("noel", "Cadeaux");
        suggestedCategoryKeywords.put("aid", "Cadeaux");
        suggestedCategoryKeywords.put("ramadan", "Cadeaux");
        suggestedCategoryKeywords.put("fete", "Cadeaux");
        suggestedCategoryKeywords.put("fete des peres", "Cadeaux");
        suggestedCategoryKeywords.put("fete des meres", "Cadeaux");
        suggestedCategoryKeywords.put("saint valentin", "Cadeaux");
        suggestedCategoryKeywords.put("amour", "Cadeaux");
        suggestedCategoryKeywords.put("fleur", "Cadeaux");
        suggestedCategoryKeywords.put("fleuriste", "Cadeaux");
        suggestedCategoryKeywords.put("chocolat", "Cadeaux");
        suggestedCategoryKeywords.put("leonidas", "Cadeaux");
        suggestedCategoryKeywords.put("godiva", "Cadeaux");
        suggestedCategoryKeywords.put("lindt", "Cadeaux");
        suggestedCategoryKeywords.put("ferrero", "Cadeaux");
        suggestedCategoryKeywords.put("carte cadeau", "Cadeaux");
        suggestedCategoryKeywords.put("bon cadeau", "Cadeaux");
        suggestedCategoryKeywords.put("cheque cadeau", "Cadeaux");
        suggestedCategoryKeywords.put("don", "Cadeaux");
        suggestedCategoryKeywords.put("donation", "Cadeaux");
        suggestedCategoryKeywords.put("charite", "Cadeaux");
        suggestedCategoryKeywords.put("association", "Cadeaux");
        suggestedCategoryKeywords.put("dons", "Cadeaux");
        suggestedCategoryKeywords.put("sadaqa", "Cadeaux");
        suggestedCategoryKeywords.put("zakat", "Cadeaux");

        // NEW: Revenus & Salaire (special handling needed)
        suggestedCategoryKeywords.put("salaire", "Revenus");
        suggestedCategoryKeywords.put("revenu", "Revenus");
        suggestedCategoryKeywords.put("revenus", "Revenus");
        suggestedCategoryKeywords.put("paie", "Revenus");
        suggestedCategoryKeywords.put("paye", "Revenus");
        suggestedCategoryKeywords.put("bulletin", "Revenus");
        suggestedCategoryKeywords.put("virement", "Revenus");
        suggestedCategoryKeywords.put("remboursement", "Revenus");
        suggestedCategoryKeywords.put("rembourse", "Revenus");
        suggestedCategoryKeywords.put("rembourser", "Revenus");
        suggestedCategoryKeywords.put("prime", "Revenus");
        suggestedCategoryKeywords.put("bonus", "Revenus");
        suggestedCategoryKeywords.put("gratification", "Revenus");
        suggestedCategoryKeywords.put("interet", "Revenus");
        suggestedCategoryKeywords.put("dividende", "Revenus");
        suggestedCategoryKeywords.put("investissement", "Revenus");
        suggestedCategoryKeywords.put("placement", "Revenus");
        suggestedCategoryKeywords.put("actions", "Revenus");
        suggestedCategoryKeywords.put("crypto", "Revenus");
        suggestedCategoryKeywords.put("bitcoin", "Revenus");
        suggestedCategoryKeywords.put("ethereum", "Revenus");
        suggestedCategoryKeywords.put("trading", "Revenus");
        suggestedCategoryKeywords.put("bourse", "Revenus");
        suggestedCategoryKeywords.put("freelance", "Revenus");
        suggestedCategoryKeywords.put("consulting", "Revenus");
        suggestedCategoryKeywords.put("projet", "Revenus");
        suggestedCategoryKeywords.put("mission", "Revenus");
        suggestedCategoryKeywords.put("prestation", "Revenus");
        suggestedCategoryKeywords.put("facture payee", "Revenus");
        suggestedCategoryKeywords.put("loc", "Revenus");
        suggestedCategoryKeywords.put("location revenu", "Revenus");
        suggestedCategoryKeywords.put("loyer recu", "Revenus");
        suggestedCategoryKeywords.put("heritage", "Revenus");
        suggestedCategoryKeywords.put("don recu", "Revenus");
        suggestedCategoryKeywords.put("loterie", "Revenus");
        suggestedCategoryKeywords.put("gain", "Revenus");

        // NEW: Services Professionnels
        suggestedCategoryKeywords.put("avocat", "Services Pro");
        suggestedCategoryKeywords.put("notaire", "Services Pro");
        suggestedCategoryKeywords.put("huissier", "Services Pro");
        suggestedCategoryKeywords.put("expert comptable", "Services Pro");
        suggestedCategoryKeywords.put("comptable", "Services Pro");
        suggestedCategoryKeywords.put("banque", "Services Pro");
        suggestedCategoryKeywords.put("banquier", "Services Pro");
        suggestedCategoryKeywords.put("conseiller", "Services Pro");
        suggestedCategoryKeywords.put("financier", "Services Pro");
        suggestedCategoryKeywords.put("assurance", "Services Pro");
        suggestedCategoryKeywords.put("agent", "Services Pro");
        suggestedCategoryKeywords.put("courtier", "Services Pro");
        suggestedCategoryKeywords.put("immobilier", "Services Pro");
        suggestedCategoryKeywords.put("agence", "Services Pro");
        suggestedCategoryKeywords.put("conseil", "Services Pro");
        suggestedCategoryKeywords.put("consultant", "Services Pro");
        suggestedCategoryKeywords.put("architecte", "Services Pro");
        suggestedCategoryKeywords.put("designer", "Services Pro");
        suggestedCategoryKeywords.put("graphiste", "Services Pro");
        suggestedCategoryKeywords.put("developpeur", "Services Pro");
        suggestedCategoryKeywords.put("programmeur", "Services Pro");
        suggestedCategoryKeywords.put("developpeur web", "Services Pro");
        suggestedCategoryKeywords.put("web designer", "Services Pro");
        suggestedCategoryKeywords.put("traducteur", "Services Pro");
        suggestedCategoryKeywords.put("interprete", "Services Pro");
        suggestedCategoryKeywords.put("photographe", "Services Pro");
        suggestedCategoryKeywords.put("videaste", "Services Pro");
        suggestedCategoryKeywords.put("monteur", "Services Pro");
        suggestedCategoryKeywords.put("redacteur", "Services Pro");
        suggestedCategoryKeywords.put("copywriter", "Services Pro");
        suggestedCategoryKeywords.put("community manager", "Services Pro");
        suggestedCategoryKeywords.put("coach", "Services Pro");
        suggestedCategoryKeywords.put("formateur", "Services Pro");
        suggestedCategoryKeywords.put("animateur", "Services Pro");
        suggestedCategoryKeywords.put("dj", "Services Pro");
        suggestedCategoryKeywords.put("orchestre", "Services Pro");
        suggestedCategoryKeywords.put("groupe", "Services Pro");
        suggestedCategoryKeywords.put("evenementiel", "Services Pro");
        suggestedCategoryKeywords.put("wedding planner", "Services Pro");
        suggestedCategoryKeywords.put("organisateur", "Services Pro");
        suggestedCategoryKeywords.put("caterer", "Services Pro");
        suggestedCategoryKeywords.put("traiteur", "Services Pro");
        suggestedCategoryKeywords.put("patissier", "Services Pro");
        suggestedCategoryKeywords.put("boulanger", "Services Pro");
        suggestedCategoryKeywords.put("plombier", "Services Pro");
        suggestedCategoryKeywords.put("electricien", "Services Pro");
        suggestedCategoryKeywords.put("menuisier", "Services Pro");
        suggestedCategoryKeywords.put("peintre", "Services Pro");
        suggestedCategoryKeywords.put("maçon", "Services Pro");
        suggestedCategoryKeywords.put("serrurier", "Services Pro");
        suggestedCategoryKeywords.put("depanneur", "Services Pro");
        suggestedCategoryKeywords.put("vitrier", "Services Pro");
        suggestedCategoryKeywords.put("tapissier", "Services Pro");
        suggestedCategoryKeywords.put("jardinier", "Services Pro");
        suggestedCategoryKeywords.put("paysagiste", "Services Pro");
        suggestedCategoryKeywords.put("pisciniste", "Services Pro");
        suggestedCategoryKeywords.put("menage", "Services Pro");
        suggestedCategoryKeywords.put("femme de menage", "Services Pro");
        suggestedCategoryKeywords.put("aide", "Services Pro");
        suggestedCategoryKeywords.put("auxiliaire", "Services Pro");
        suggestedCategoryKeywords.put("garde enfant", "Services Pro");
        suggestedCategoryKeywords.put("nounou", "Services Pro");
        suggestedCategoryKeywords.put("baby sitter", "Services Pro");
        suggestedCategoryKeywords.put("auxiliaire vie", "Services Pro");
        suggestedCategoryKeywords.put("aide domicile", "Services Pro");
        suggestedCategoryKeywords.put("demenagement", "Services Pro");
        suggestedCategoryKeywords.put("demenageur", "Services Pro");
        suggestedCategoryKeywords.put("box", "Services Pro");
        suggestedCategoryKeywords.put("stockage", "Services Pro");
    }

    private void buildCategoryKeywordMap() {
        for (Category cat : availableCategories) {
            String normalized = normalizeText(cat.name);
            categoryKeywordMap.put(normalized, cat.name);
        }
    }

    // Add this method to check if network is available
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network: " + e.getMessage());
        }
        return false;
    }

    public String getProviderStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Groq: ").append(groqAvailable ? "✓" : "✗");
        status.append(" | Gemini: ").append(geminiAvailable ? "✓" : "✗");
        status.append(" | Keys: GROQ=").append(BuildConfig.GROQ_API_KEY != null && !BuildConfig.GROQ_API_KEY.isEmpty() ? "set" : "EMPTY");
        status.append(", GEMINI=").append(BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.isEmpty() ? "set" : "EMPTY");
        return status.toString();
    }
    
    public boolean isGroqAvailable() { return groqAvailable; }
    public boolean isGeminiAvailable() { return geminiAvailable; }

    public void analyzeWithValidation(String text, String currency, CategoryCheckCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onAnalysisFailed("Texte vide");
            return;
        }

        // Check learning cache first
        AnalysisResult cached = checkLearningCache(text);
        if (cached != null) {
            Log.d(TAG, "Using cached result");
            callback.onAnalysisComplete(cached);
            return;
        }

        // Check network connectivity - if offline, skip AI and go directly to regex
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network available, using regex directly");
            AnalysisResult result = analyzeWithRegex(text, currency);
            if (result.success) {
                if (!result.categoryExistsInApp) {
                    callback.onCategoryMissing(result.category, result);
                } else {
                    callback.onAnalysisComplete(result);
                }
            } else {
                callback.onAnalysisFailed("Impossible d'analyser. Vérifiez votre connexion.");
            }
            return;
        }

        Log.d(TAG, "Starting analysis. " + getProviderStatus());

        // Try Groq first (free), then Gemini, then regex
        if (groqAvailable) {
            analyzeWithGroq(text, currency, callback);
        } else if (geminiAvailable) {
            analyzeWithGemini(text, currency, callback);
        } else {
            Log.w(TAG, "No AI providers available! Falling back to regex. " + getProviderStatus());
            AnalysisResult result = analyzeWithRegex(text, currency);
            if (result.success) {
                if (!result.categoryExistsInApp) {
                    callback.onCategoryMissing(result.category, result);
                } else {
                    callback.onAnalysisComplete(result);
                }
            } else {
                callback.onAnalysisFailed("Clés API manquantes. Ajoutez GROQ_API_KEY dans local.properties");
            }
        }
    }

    private void handleAIResult(AnalysisResult result, String suggestedCategory, CategoryCheckCallback callback) {
        if (result.category != null && !result.category.isEmpty()) {
            boolean exactMatch = categoryExistsExact(result.category);
            String matchedCategory = findBestMatchingCategory(result.category);

            if (exactMatch) {
                result.categoryExistsInApp = true;
                callback.onAnalysisComplete(result);
            } else if (matchedCategory != null) {
                result.category = matchedCategory;
                result.categoryExistsInApp = true;
                result.confidence = Math.min(result.confidence, 0.85);
                callback.onAnalysisComplete(result);
            } else {
                // New category - let user add it
                result.categoryExistsInApp = false;
                callback.onCategoryMissing(result.category, result);
            }
        } else {
            result.errorMessage = "Catégorie non détectée";
            callback.onAnalysisFailed(result.errorMessage);
        }
    }

    private void fallbackToNextProvider(String text, String currency, String failedProvider, CategoryCheckCallback callback) {
        if ("groq".equals(failedProvider) && geminiAvailable) {
            analyzeWithGemini(text, currency, callback);
        } else if ("gemini".equals(failedProvider) && groqAvailable) {
            analyzeWithGroq(text, currency, callback);
        } else {
            AnalysisResult result = analyzeWithRegex(text, currency);
            if (result.success) {
                if (!result.categoryExistsInApp) {
                    callback.onCategoryMissing(result.category, result);
                } else {
                    callback.onAnalysisComplete(result);
                }
            } else {
                callback.onAnalysisFailed("Impossible d'analyser. Vérifiez votre connexion.");
            }
        }
    }

    // ============================================
    // GROQ API (Free, Open Llama Model)
    // ============================================

    private void analyzeWithGroq(String text, String currency, CategoryCheckCallback callback) {
        Log.d(TAG, "Sending to Groq API: " + text);
        String prompt = buildSmartPrompt(text, currency);

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "You are an expense analyzer. Return ONLY valid JSON, no markdown.");

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        messages.add(systemMsg);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", "llama-3.3-70b-versatile");
        body.add("messages", messages);
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", 500);

        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + BuildConfig.GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Groq network failure: " + e.getMessage() + ". Falling back...");
                mainHandler.post(() -> fallbackToNextProvider(text, currency, "groq", callback));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Groq HTTP error " + response.code() + ": " + errorBody);
                    mainHandler.post(() -> fallbackToNextProvider(text, currency, "groq", callback));
                    return;
                }

                try {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();

                    AnalysisResult result = parseAIResponse(content, text, currency);
                    result.rawAIResponse = content;
                    result.isAIGenerated = true;
                    result.aiProvider = "groq";

                    mainHandler.post(() -> handleAIResult(result, result.category, callback));
                } catch (Exception e) {
                    Log.e(TAG, "Groq parse error: " + e.getMessage());
                    mainHandler.post(() -> fallbackToNextProvider(text, currency, "groq", callback));
                }
            }
        });
    }

    // ============================================
    // GEMINI API
    // ============================================

    private void analyzeWithGemini(String text, String currency, CategoryCheckCallback callback) {
        String prompt = buildSmartPrompt(text, currency);
        Content content = new Content.Builder().addText(prompt).build();

        ListenableFuture<GenerateContentResponse> future = geminiModel.generateContent(content);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse response) {
                try {
                    String rawResponse = response.getText();
                    AnalysisResult result = parseAIResponse(rawResponse, text, currency);
                    result.rawAIResponse = rawResponse;
                    result.isAIGenerated = true;
                    result.aiProvider = "gemini";

                    mainHandler.post(() -> handleAIResult(result, result.category, callback));
                } catch (Exception e) {
                    Log.e(TAG, "Gemini parse error: " + e.getMessage());
                    mainHandler.post(() -> fallbackToNextProvider(text, currency, "gemini", callback));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini failed: " + t.getMessage());
                mainHandler.post(() -> fallbackToNextProvider(text, currency, "gemini", callback));
            }
        }, Runnable::run);
    }

    // ============================================
    // IMPROVED PROMPT (Date + Category)
    // ============================================

    private String buildSmartPrompt(String text, String currency) {
        StringBuilder categoriesList = new StringBuilder();
        for (Category cat : availableCategories) {
            categoriesList.append("- ").append(cat.name).append("\n");
        }

        String todayStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        String yesterdayStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(yesterday.getTime());

        return "Analyze this expense description and return JSON.\n\n" +
                "Input: \"" + text + "\"\n" +
                "User currency: " + currency + "\n" +
                "TODAY's date is: " + todayStr + "\n" +
                "YESTERDAY's date is: " + yesterdayStr + "\n\n" +
                "EXISTING CATEGORIES (use EXACT name ONLY if it fits perfectly):\n" + categoriesList.toString() + "\n\n" +
                "CRITICAL: Gaming (souris, clavier, jeux vidéo), Électronique, Vétérinaire, etc. are NOT Loisirs or Santé. " +
                "If the expense type is NOT in the list above, suggest a NEW category name (e.g. Gaming, Électronique, Vétérinaire). " +
                "The app will ask the user to create it before continuing.\n\n" +
                "Return ONLY this JSON (no markdown):\n" +
                "{\"amount\":number,\"currency\":\"string\",\"description\":\"string\",\"category\":\"string\",\"date\":\"string\",\"confidence\":number}\n\n" +
                "RULES:\n" +
                "1. amount: positive number only\n" +
                "2. category: EXACT name from list if match, OR new category name if no match (e.g. Gaming, Électronique)\n" +
                "3. date: dd/MM/yyyy or \"today\"/\"yesterday\". If no date in text, use \"today\". " +
                "Parse: hier=yesterday, aujourd'hui=today, avant-hier=" + getDateBeforeYesterday() + ", lundi/mardi=that day this week\n" +
                "4. description: clean text, no prices/dates\n" +
                "5. confidence: 0-1, lower if category is new or uncertain";
    }

    private String getDateBeforeYesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -2);
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime());
    }

    private AnalysisResult parseAIResponse(String jsonText, String originalText, String defaultCurrency) {
        AnalysisResult result = new AnalysisResult();

        try {
            String cleanJson = extractJsonFromText(jsonText);
            JsonObject json = JsonParser.parseString(cleanJson).getAsJsonObject();

            if (json.has("amount")) {
                result.amount = json.get("amount").getAsDouble();
                if (result.amount <= 0) result.amount = extractAmountWithRegex(originalText);
            } else {
                result.amount = extractAmountWithRegex(originalText);
            }

            result.currency = json.has("currency") ? json.get("currency").getAsString() : defaultCurrency;

            result.description = json.has("description") ?
                    cleanDescription(json.get("description").getAsString()) : generateDescription(originalText);

            result.category = json.has("category") ? json.get("category").getAsString().trim() : "";

            String dateStr = json.has("date") ? json.get("date").getAsString() : "today";
            result.date = normalizeDate(dateStr);

            result.confidence = json.has("confidence") ? json.get("confidence").getAsDouble() : 0.6;

            result.success = result.amount > 0;
            if (result.category.isEmpty()) {
                result.category = extractCategoryWithRegex(originalText);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            result.errorMessage = e.getMessage();
            result.success = false;
        }

        return result;
    }

    // ============================================
    // REGEX FALLBACK (Improved Date Parsing)
    // ============================================

    private AnalysisResult analyzeWithRegex(String text, String currency) {
        AnalysisResult result = new AnalysisResult();
        result.isAIGenerated = false;
        result.aiProvider = "regex";
        result.confidence = 0.5;

        result.amount = extractAmountWithRegex(text);
        result.date = extractDateWithRegex(text);
        result.category = extractCategoryWithRegex(text);
        
        // If category is null or doesn't exist in app, mark as missing
        if (result.category == null || result.category.isEmpty()) {
            result.categoryExistsInApp = false;
            // Try to extract a category name from the text for suggestion
            result.category = suggestCategoryFromText(text);
        } else {
            result.categoryExistsInApp = categoryExists(result.category);
        }
        
        result.description = generateDescription(text);
        result.currency = currency;
        result.success = result.amount > 0;

        Log.d(TAG, "Regex analysis: amount=" + result.amount + ", category=" + result.category + ", exists=" + result.categoryExistsInApp);
        return result;
    }

    private String suggestCategoryFromText(String text) {
        // Try to extract a potential category name from the text
        // This is used when no keyword matches - we try to guess from context
        String normalized = normalizeText(text);
        
        // Check for common words that might indicate a category
        Map<String, String> contextKeywords = new HashMap<>();
        contextKeywords.put("achat", "Achat");
        contextKeywords.put("achète", "Achat");
        contextKeywords.put("acheter", "Achat");
        contextKeywords.put("achat", "Shopping");
        contextKeywords.put("achète", "Shopping");
        contextKeywords.put("acheté", "Shopping");
        
        for (Map.Entry<String, String> entry : contextKeywords.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // If still no match, try to extract a noun that might be the category
        String[] words = text.split("\\s+");
        for (String word : words) {
            word = word.replaceAll("[^a-zA-Zà-ÿ]", "").toLowerCase();
            if (word.length() > 3 && !isCommonWord(word)) {
                // Capitalize first letter
                return word.substring(0, 1).toUpperCase() + word.substring(1);
            }
        }
        
        return "Autre";
    }
    
    private boolean isCommonWord(String word) {
        Set<String> commonWords = new HashSet<>(Arrays.asList(
            "hier", "aujourd", "demain", "pour", "avec", "dans", "sur", "sous", "vers",
            "très", "trop", "peu", "plus", "moins", "tout", "rien", "tous", "toutes",
            "some", "any", "the", "and", "but", "for", "with", "from", "this", "that"
        ));
        return commonWords.contains(word.toLowerCase());
    }

    private double extractAmountWithRegex(String text) {
        Pattern pattern = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*(dh|€|\\$|da|dt|cfa|usd|eur|mad)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text.toLowerCase());
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(",", "."));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String extractDateWithRegex(String text) {
        text = text.toLowerCase();

        // French month names map
        Map<String, Integer> frenchMonths = new HashMap<>();
        frenchMonths.put("janvier", 1);
        frenchMonths.put("fevrier", 2);
        frenchMonths.put("février", 2);
        frenchMonths.put("mars", 3);
        frenchMonths.put("avril", 4);
        frenchMonths.put("mai", 5);
        frenchMonths.put("juin", 6);
        frenchMonths.put("juillet", 7);
        frenchMonths.put("aout", 8);
        frenchMonths.put("août", 8);
        frenchMonths.put("septembre", 9);
        frenchMonths.put("octobre", 10);
        frenchMonths.put("novembre", 11);
        frenchMonths.put("decembre", 12);
        frenchMonths.put("décembre", 12);

        if (text.contains("avant-hier") || text.contains("day before yesterday")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -2);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime());
        }
        if (text.contains("hier") || text.contains("yesterday")) return "yesterday";
        if (text.contains("aujourd'hui") || text.contains("today")) return "today";

        // "il y a X jours"
        Pattern daysAgo = Pattern.compile("il y a (\\d+) jour", Pattern.CASE_INSENSITIVE);
        Matcher m = daysAgo.matcher(text);
        if (m.find()) {
            try {
                int days = Integer.parseInt(m.group(1));
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -days);
                return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime());
            } catch (Exception ignored) {}
        }

        // French date: "le 8 septembre 2024" or "8 septembre 2024" or "8 septembre"
        Pattern frenchDatePattern = Pattern.compile("(?:le\\s+)?(\\d{1,2})\\s+(janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)(?:\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
        Matcher frenchMatcher = frenchDatePattern.matcher(text);
        if (frenchMatcher.find()) {
            try {
                int day = Integer.parseInt(frenchMatcher.group(1));
                String monthStr = frenchMatcher.group(2).toLowerCase();
                String yearStr = frenchMatcher.group(3);
                
                Integer month = frenchMonths.get(monthStr);
                if (month != null) {
                    int year = Calendar.getInstance().get(Calendar.YEAR);
                    if (yearStr != null) {
                        year = Integer.parseInt(yearStr);
                    }
                    
                    Calendar cal = Calendar.getInstance();
                    cal.setLenient(false);
                    cal.set(year, month - 1, day);
                    cal.getTime(); // Validate
                    return String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
                }
            } catch (Exception ignored) {}
        }

        // Month-first format: "septembre 8 2024" or "septembre 8"
        Pattern monthFirstPattern = Pattern.compile("(janvier|février|fevrier|mars|avril|mai|juin|juillet|août|aout|septembre|octobre|novembre|décembre|decembre)\\s+(\\d{1,2})(?:\\s+(\\d{4}))?", Pattern.CASE_INSENSITIVE);
        Matcher monthFirstMatcher = monthFirstPattern.matcher(text);
        if (monthFirstMatcher.find()) {
            try {
                String monthStr = monthFirstMatcher.group(1).toLowerCase();
                int day = Integer.parseInt(monthFirstMatcher.group(2));
                String yearStr = monthFirstMatcher.group(3);
                
                Integer month = frenchMonths.get(monthStr);
                if (month != null) {
                    int year = Calendar.getInstance().get(Calendar.YEAR);
                    if (yearStr != null) {
                        year = Integer.parseInt(yearStr);
                    }
                    
                    Calendar cal = Calendar.getInstance();
                    cal.setLenient(false);
                    cal.set(year, month - 1, day);
                    cal.getTime(); // Validate
                    return String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
                }
            } catch (Exception ignored) {}
        }

        // Date patterns: dd/mm/yyyy, dd-mm-yyyy, dd/mm, yyyy-mm-dd
        Pattern datePattern = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?");
        Matcher matcher = datePattern.matcher(text);
        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : Calendar.getInstance().get(Calendar.YEAR);
                if (year < 100) year += 2000;

                Calendar cal = Calendar.getInstance();
                cal.setLenient(false);
                cal.set(year, month - 1, day);
                cal.getTime();
                return String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year);
            } catch (Exception ignored) {}
        }

        return "today";
    }

    private String extractCategoryWithRegex(String text) {
        String normalized = normalizeText(text);
        // First: check suggested NEW categories (Gaming, Électronique, Vétérinaire, etc.)
        for (Map.Entry<String, String> entry : suggestedCategoryKeywords.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                Log.d(TAG, "Found suggested NEW category keyword: " + entry.getKey() + " -> " + entry.getValue());
                return entry.getValue(); // Return the NEW category name (will trigger missing category dialog)
            }
        }
        // Then: existing category keywords
        for (Map.Entry<String, String> entry : categoryKeywordMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                Log.d(TAG, "Found existing category keyword: " + entry.getKey() + " -> " + entry.getValue());
                return entry.getValue();
            }
        }
        // Return null to indicate category could not be determined
        // This allows analyzeWithRegex to properly set categoryExistsInApp = false
        Log.d(TAG, "No category keyword found in text: " + text);
        return null;
    }

    // ============================================
    // HELPERS
    // ============================================

    private boolean categoryExistsExact(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) return false;
        String normalized = normalizeText(categoryName);
        for (Category cat : availableCategories) {
            if (normalizeText(cat.name).equals(normalized)) return true;
        }
        return false;
    }

    private boolean categoryExists(String categoryName) {
        return categoryExistsExact(categoryName);
    }

    public String findBestMatchingCategory(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String normalized = normalizeText(input);

        for (Category cat : availableCategories) {
            if (normalizeText(cat.name).equals(normalized)) return cat.name;
        }
        for (Category cat : availableCategories) {
            String catNorm = normalizeText(cat.name);
            if (catNorm.contains(normalized) || normalized.contains(catNorm)) return cat.name;
        }
        // Do NOT map suggested new categories (Gaming, Électronique, Vétérinaire) to generic ones (Loisirs, Santé)
        for (String suggested : suggestedCategoryKeywords.values()) {
            if (normalizeText(suggested).equals(normalized) && !categoryExistsExact(suggested)) {
                return null; // User must create this category
            }
        }
        for (Map.Entry<String, String> entry : categoryKeywordMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                for (Category cat : availableCategories) {
                    if (cat.name.equals(entry.getValue())) return cat.name;
                }
            }
        }
        int bestDistance = Integer.MAX_VALUE;
        String bestMatch = null;
        for (Category cat : availableCategories) {
            int d = calculateLevenshteinDistance(normalized, normalizeText(cat.name));
            if (d < 3 && d < bestDistance) {
                bestDistance = d;
                bestMatch = cat.name;
            }
        }
        return bestMatch;
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null) return "today";
        dateStr = dateStr.toLowerCase().trim();

        if (dateStr.equals("today") || dateStr.equals("aujourd'hui")) return "today";
        if (dateStr.equals("yesterday") || dateStr.equals("hier")) return "yesterday";

        String[] formats = {"dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yy"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
                }
            } catch (Exception ignored) {}
        }
        return "today";
    }

    private String cleanDescription(String desc) {
        if (desc == null) return "";
        return desc.replaceAll("\\d+(\\.\\d+)?\\s*(dh|€|\\$)?", "")
                .replaceAll("\\d{1,2}[/-]\\d{1,2}[/-]?\\d{0,4}", "")
                .replaceAll("hier|aujourd'hui|today|yesterday", "")
                .trim();
    }

    private String generateDescription(String text) {
        String desc = text.replaceAll("\\d+(?:[.,]\\d{1,2})?\\s*(dh|€|\\$|da|dt|cfa)?", "")
                .replaceAll("\\d{1,2}[/-]\\d{1,2}[/-]?\\d{0,4}", "")
                .replaceAll("hier|aujourd'hui|today|yesterday", "")
                .trim();
        return desc.isEmpty() ? "Dépense" : desc;
    }

    private String extractJsonFromText(String text) {
        text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // ============================================
    // LEARNING
    // ============================================

    public void learnFromCorrection(String originalText, AnalysisResult correctedResult) {
        String key = normalizeText(originalText);
        learningPrefs.edit()
                .putString(key + "_category", correctedResult.category)
                .putString(key + "_desc", correctedResult.description)
                .apply();
    }

    public AnalysisResult checkLearningCache(String text) {
        String key = normalizeText(text);
        String cachedCategory = learningPrefs.getString(key + "_category", null);
        if (cachedCategory != null) {
            AnalysisResult result = new AnalysisResult();
            result.category = cachedCategory;
            result.description = learningPrefs.getString(key + "_desc", "");
            result.amount = extractAmountWithRegex(text);
            result.date = extractDateWithRegex(text);
            result.confidence = 0.95;
            result.isAIGenerated = false;
            result.success = true;
            result.categoryExistsInApp = categoryExists(cachedCategory);
            return result;
        }
        return null;
    }

    public String generateRandomColor() {
        String[] colors = {"#FF5722", "#2196F3", "#4CAF50", "#E91E63", "#F44336",
                "#9C27B0", "#FF9800", "#795548", "#607D8B", "#3F51B5"};
        return colors[random.nextInt(colors.length)];
    }

    public boolean isExpenseText(String text) {
        return extractAmountWithRegex(text) > 0;
    }
}
