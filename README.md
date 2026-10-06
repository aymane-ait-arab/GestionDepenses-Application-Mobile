# 💰 Gestionnaire de Dépenses — Android Expense Tracker

A full-featured Android expense-tracking app with cloud sync, visual analytics, and an AI-assisted natural-language expense entry.

> 📎 Based on the project presentation *"Gestionnaire de Dépenses"* — Faculté des Sciences, UMI, 2025/2026.

## The problem

Personal budget tracking is tedious: expenses are forgotten, manual categorization is frustrating, there's no clear visualization of spending, and long-term trend analysis is hard — leading to unnoticed budget overruns.

## The solution

A Material Design 3 Android app that makes expense tracking fast: quick entry, clear visual statistics, and secure cloud sync via Firebase — so spending data stays up to date and accessible across devices.

## Key features

| Feature | Description |
|---|---|
| Suivi des dépenses | Add, edit, delete expenses with amount, date, category |
| Catégories | Customizable categories (Food, Transport, Leisure, etc.) |
| Filtres & recherche | Filter by period, category, or search a specific expense |
| Statistiques visuelles | Pie and bar charts showing where money goes |
| Export de rapports | Generate PDF and CSV reports for archiving/sharing |
| Synchronisation cloud | Data saved to Firebase, accessible on all devices |
| Fonctionnement hors ligne | Local cache with automatic sync on reconnection |
| Multi-devises | DH, €, $ and other currencies supported |

## Architecture — MVVM

```
USER → ACTIVITY (UI) → VIEWMODEL (LiveData) → REPOSITORY → FIREBASE (Auth + Firestore)
         ↑ Observe/Update        ↑ Request/LiveData      ↑ API/Data
```

<img width="447" height="764" alt="_Présentation projet entreprise moderne simple" src="https://github.com/user-attachments/assets/257bc3f4-7e33-439d-8996-6136582fbab2" />


- **Interface (View):** Activities & Fragments display the UI — what the user sees.
- **Logic (ViewModel):** Holds business logic and exposes data via `LiveData`.
- **Data (Repository):** The single access point to Firebase Auth (login) and Firestore (cloud storage) — Activities/ViewModels never talk to Firebase directly.

## Tech stack

- **Language:** Java 17
- **Platform:** Android SDK 34
- **Backend:** Firebase Authentication + Cloud Firestore
- **UI:** Material Design 3
- **Charts:** MPAndroidChart (pie + bar charts)

## Smart expense entry (AI-assisted, optional)

A natural-language input helper lets users type a sentence like *"Déjeuner 45dh hier au McDo"* instead of filling a form. The text is parsed through a **4-level fallback pipeline**, each level only triggering if the previous one fails or times out:

1. **Cache d'apprentissage** — checks if a similar phrase was already corrected before; cache hit returns instantly with ~95% confidence.
2. **Groq API (primary)** — `llama-3.3-70b`, 5-second timeout, returns structured JSON.
3. **Gemini API (fallback)** — Google Gemini 1.5 Flash, used if Groq is unavailable.
4. **Regex fallback (offline)** — 800+ keyword-based regular expressions, works 100% offline if both AI APIs fail or there's no connection.

This feature is purely an accelerator — manual form entry is always available as a fallback.

## Screens

- **Connexion/Inscription** — Firebase-secured login and signup (name, email, password, country selection)
- **Tableau de bord** — total spending overview + recent expenses list
- **Ajouter dépense** — smart natural-language entry OR manual form
- **Gestion des catégories** — create/edit/delete custom categories
- **Statistiques** — visual breakdown by period (week/month/year) and category, with trend chart
- **Profil/Export** — account info, PDF/CSV export for sharing and archiving

## Results

- ✅ Full CRUD operations for expenses
- ✅ Real-time Firebase sync across devices
- ✅ Hierarchical, customizable category system
- ✅ Multi-currency support
- ✅ Offline mode with local cache + auto-sync on reconnection
- ✅ PDF & CSV report export

## What I'd improve next

- Add budget limits per category with push-notification alerts on overrun
- Add recurring-expense templates (subscriptions, rent)
- Expand the AI parser's confidence feedback loop so low-confidence cache entries get re-validated by the user

## Authors

Aymane Ait Arab & Kawthar Derouich — Faculté des Sciences, Université Moulay Ismaïl (UMI)
Encadré par : Mr. MAADA Loukmane, Mr. AGHOUTANE Badreddine
