# Πρωτόκολλο υπαγόρευσης (κινητό ↔ backend)

Η ροή, η σειρά των μηνυμάτων και παραδείγματα. **Τα πεδία και οι τύποι τους ζουν μόνο στο [`backend/src/pesto_server/protocol.py`](../backend/src/pesto_server/protocol.py)**: αν κάτι εδώ διαφωνεί με εκείνο, ισχύει εκείνο.

## Σύνδεση

```
ws://<host>:8000/v1/dictate
Authorization: Bearer <token της συσκευής>
```

- Κάθε κινητό έχει **δικό του token**, από το pairing (παρακάτω). Τα εργαλεία στο Mac (`tools/ws_client.py`) χρησιμοποιούν το `PESTO_APP_TOKEN` του `backend/.env`, που δεν φτάνει ποτέ στο κινητό. Αν το header δεν βολεύει, γίνεται δεκτό και `?token=<token>`.
- Χωρίς σωστό token (λάθος, ανακληθέν, κενό) ο server κλείνει τη σύνδεση πριν από το handshake με κωδικό **1008**. Ο client βλέπει την άρνηση του handshake (HTTP 403).
- Από προεπιλογή ο server ακούει στο `127.0.0.1`. Για το κινητό στο ίδιο Wi-Fi: `HOST=0.0.0.0` στο `.env`. Ο emulator βλέπει το laptop στο `10.0.2.2`.
- Μία υπαγόρευση = μία σύνδεση. Μετά το `final` (ή το `cancel`) ο server κλείνει τη σύνδεση.
- Δεν υπάρχουν `/docs`, `/redoc` και `/openapi.json`: χωρίς token δεν φαίνεται τίποτα για το API.

## Pairing: token ανά συσκευή

Το token δεν είναι μέσα στο APK. Κάθε συσκευή το παίρνει μία φορά, με κωδικό μίας χρήσης:

1. Στο Mac: `uv run pesto-server pair` (με τον server να τρέχει ή όχι). Τυπώνει έναν κωδικό **8 ψηφίων**, που λήγει σε **10 λεπτά**.
2. Το κινητό στέλνει τον κωδικό και ένα όνομα:

```
POST http://<host>:8000/v1/pair
Content-Type: application/json
```

```json
{"code": "48213907", "device_name": "POCO X7 Pro"}
```

3. Ο backend απαντά με το token της συσκευής, που το κινητό κρατά κρυπτογραφημένο:

```json
{"token": "q3T0…"}
```

| Απάντηση | Πότε |
|---|---|
| `200` `{"token": …}` | Σωστός κωδικός |
| `403` `{"detail": "wrong code"}` | Λάθος, χρησιμοποιημένος ή ληγμένος κωδικός, ή κανένας ενεργός κωδικός |
| `422` | Άγνωστο πεδίο, πεδίο που λείπει, ή πολύ μεγάλο (`code` ως 32, `device_name` ως 100 χαρακτήρες) |

- Ο κωδικός πετιέται μετά την πρώτη χρήση ή μετά από **5 λάθος προσπάθειες**. Υπάρχει ένας ενεργός κωδικός τη φορά: νέο `pair` αντικαθιστά τον προηγούμενο.
- Ο backend κρατά μόνο το **sha256** του token (και του κωδικού), με το όνομα και την ημερομηνία, στο `backend/data/devices.json` (εκτός git). Κανένα log δεν γράφει κωδικό ή token.
- Νέο pairing με το ίδιο όνομα αντικαθιστά την παλιά συσκευή (π.χ. μετά από επανεγκατάσταση).
- `uv run pesto-server devices`: οι συσκευές. `uv run pesto-server revoke <όνομα>`: η επόμενη σύνδεση της συσκευής απορρίπτεται, χωρίς restart.

## Ροή

```
κινητό                                   backend                    Soniox
  │ start (JSON)                            │                          │
  │────────────────────────────────────────►│ connect + config ───────►│
  │                                ready    │                          │
  │◄────────────────────────────────────────│                          │
  │ ήχος (binary, 100 ms ανά frame)          │ ήχος ───────────────────►│
  │────────────────────────────────────────►│◄──────────── tokens ─────│
  │                              partial    │                          │
  │◄────────────────────────────────────────│                          │
  │ pause / resume (προαιρετικά)             │ keepalive κάθε 10 s ────►│
  │ finalize (✓)                            │ finalize ───────────────►│
  │────────────────────────────────────────►│◄────────────── <fin> ────│
  │                                         │ profile (Haiku), guards  │
  │                                final    │                          │
  │◄────────────────────────────────────────│ close ──────────────────►│
```

1. Το κινητό στέλνει **`start`** ως πρώτο μήνυμα. Οποιοδήποτε άλλο πρώτο μήνυμα δίνει `error` `bad_request`.
2. Περιμένει **`ready`** πριν στείλει ήχο. Ήχος που φτάνει νωρίτερα δεν χάνεται (μπαίνει σε ουρά), αλλά το `ready` λέει ότι το Soniox είναι συνδεδεμένο.
3. Στέλνει **ήχο** ως binary frames: PCM 16-bit little-endian, mono, 16 kHz. Προτείνεται 100 ms ανά frame (3.200 bytes).
4. Όσο μιλάει ο χρήστης, έρχονται **`partial`**.
5. Στο ✓ στέλνει **`finalize`** και παίρνει **ένα** `final`.
6. Στο ✕ στέλνει **`cancel`**: δεν έρχεται `final` και τίποτα δεν καταγράφεται. Αν κλείσει απλώς τη σύνδεση, ισχύει το ίδιο.

## Παραδείγματα

### Κινητό → backend

```json
{"type": "start",
 "language_hints": ["el", "en"],
 "language_hints_strict": false,
 "terms": ["Πολιτάκης", "Pesto"],
 "profile": "mail",
 "field_context": {"package": "com.google.android.gm", "input_type": "text", "hint": "Compose email", "single_line": false, "is_password": false, "is_incognito": false},
 "sample_rate": 16000,
 "encoding": "pcm_s16le"}
```

Όλα τα πεδία εκτός από το `type` είναι προαιρετικά: `{"type": "start"}` σημαίνει ελληνικά + αγγλικά, χωρίς όρους, profile `raw`. Άγνωστο ή λάθος γραμμένο πεδίο δίνει `error` `bad_request`, ώστε τα λάθη να φαίνονται αμέσως.

Τιμές του `field_context.input_type` (τις στέλνει το κινητό από τον τύπο του πεδίου, `FieldRules.inputTypeName` στο Android): `text`, `email` (διεύθυνση), `email_subject`, `short_message`, `long_message`, `person_name`, `postal_address`, `uri`, `web_edit`, `filter` (αναζήτηση), `number`, `phone`, `datetime`, `password`. Ό,τι άλλο στέλνεται ως `text`. Το `hint` το γράφει η εφαρμογή (ή μια ιστοσελίδα), άρα είναι κείμενο τρίτου, κομμένο στους 100 χαρακτήρες. Σε πεδία `password`, `number`, `phone` και `datetime` το κουμπί δεν εμφανίζεται, οπότε δεν φτάνουν ποτέ εδώ.

```json
{"type": "pause"}
{"type": "resume"}
{"type": "finalize"}
{"type": "cancel"}
```

### Backend → κινητό

```json
{"type": "ready"}
```

```json
{"type": "partial", "final_text": "Καλημέρα Γιώργο, το ", "pending_text": "meeting πάει"}
```

- `final_text`: όλο το σταθερό κείμενο από την αρχή. Δεν αλλάζει πια.
- `pending_text`: το κείμενο που μπορεί ακόμα να αλλάξει. Κάθε `partial` το **αντικαθιστά**.
- Για εμφάνιση: `final_text + pending_text`.

```json
{"type": "final",
 "raw_text": "Καλημέρα, Γιώργο. Το meeting πάει για την Πέμπτη στις 10:00. Μπορείς να έρθεις;",
 "text": "Καλημέρα Γιώργο,\n\nΤο meeting πάει για την Πέμπτη στις 10:00. Μπορείς να έρθεις;\n\nΕυχαριστώ εκ των προτέρων.",
 "profile": "mail",
 "fallback_reason": null,
 "end_reason": "user",
 "stats": {"audio_s": 5.7, "words": 17, "stt_ms": 152, "llm_ms": 1496, "total_ms": 1648}}
```

- `text`: αυτό που μπαίνει στο πεδίο.
- `raw_text`: τι έγραψε το Soniox (για το History και το re-style).
- `fallback_reason`: `null` αν όλα πήγαν καλά. Αλλιώς ο λόγος για τον οποίο το `text` είναι το σκέτο transcript:

  | Τιμή | Τι έγινε |
  |---|---|
  | `guard:input` | Το κείμενο έμοιαζε με εντολή προς το μοντέλο, το Haiku δεν κλήθηκε |
  | `guard:empty`, `guard:length`, `guard:language`, `guard:overlap`, `guard:phrase`, `guard:leak`, `guard:answered`, `guard:code`, `guard:added` | Η έξοδος του Haiku δεν έμοιαζε με ξαναγραμμένο κείμενο (π.χ. απάντησε, έγραψε κώδικα, πρόσθεσε δικό του περιεχόμενο) και πετάχτηκε |
  | `llm:timeout`, `llm:failed`, `llm:wrong_model`, `llm:bad_output` | Η κλήση στο Haiku απέτυχε |

- `end_reason`: `user` (✓), `silence` (30 s χωρίς νέο κείμενο), `pause_limit` (παύση πάνω από 2 λεπτά), `max_duration` (10 λεπτά ήχου), `error` (το Soniox απέτυχε στη μέση: έρχεται πρώτα `error` και μετά `final` με ό,τι είχε γραφτεί).
- `stats.total_ms`: χρόνος από το `finalize` (ή το αυτόματο τέλος) μέχρι το `final`.

```json
{"type": "error", "code": "unknown_profile", "message": "no profile named 'x'", "retryable": false}
```

| `code` | Πότε | Τι ακολουθεί |
|---|---|---|
| `bad_request` | Λάθος JSON, άγνωστο πεδίο, πρώτο μήνυμα όχι `start`, δεύτερο `start` | Στο πρώτο μήνυμα: κλείσιμο. Στη μέση: η υπαγόρευση συνεχίζει |
| `unknown_profile` | Το `profile` δεν υπάρχει στο `backend/profiles/` | Κλείσιμο |
| `stt_unavailable` | Δεν έγινε σύνδεση με το Soniox | Κλείσιμο. `retryable: true` |
| `stt` | Το Soniox απέτυχε στη μέση | `final` με ό,τι γράφτηκε (αν γράφτηκε κάτι), μετά κλείσιμο. `retryable: true` |

## Όρια και χρονόμετρα (στο `backend/.env`, αν χρειαστεί αλλαγή)

| Ρύθμιση | Προεπιλογή | Τι κάνει |
|---|---|---|
| `SILENCE_TIMEOUT_S` | 30 | Τέλος αν δεν έρθει νέο κείμενο τόσα δευτερόλεπτα (όχι μέσα σε παύση) |
| `PAUSE_LIMIT_S` | 120 | Μέγιστη διάρκεια μιας παύσης |
| `MAX_AUDIO_S` | 600 | Μέγιστη διάρκεια ήχου. Ο χρόνος της παύσης δεν μετράει |
| `KEEPALIVE_INTERVAL_S` | 10 | Keepalive προς το Soniox στην παύση (το Soniox κλείνει μετά από 20 s χωρίς ήχο) |
| `FINALIZE_TIMEOUT_S` | 10 | Πόσο περιμένει το `<fin>` του Soniox |

Στην παύση το κινητό **δεν** στέλνει ήχο. Αν στείλει, ο backend τον πετάει.

## Τι κάνει το κινητό (Φάση 2), όχι ο backend

- Κενό πριν το κείμενο και κεφαλαίο στην αρχή πρότασης (C5): ο backend στέλνει το κείμενο όπως το έγραψε το Soniox ή το Haiku.
- Πεδίο κωδικού: το κουμπί δεν εμφανίζεται (C7). Ο backend δεν βλέπει ποτέ τέτοια υπαγόρευση.
- Incognito: τίποτα στο History (C7). Ο backend κρατά μόνο αριθμούς (`backend/logs/metrics.jsonl`), ποτέ κείμενο.

## Βοηθός (X3): `POST /v1/assistant`

Κλήση HTTP με `Authorization: Bearer <token της συσκευής>` στο header (το `?token=` **δεν** γίνεται δεκτό εδώ). Ο έλεγχος του token εκτελείται πριν διαβαστεί το body (`403 {"detail": "unauthorized"}`), και το μέγεθος του body περιορίζεται streaming στα **16 KB** (`413 {"detail": "body_too_large"}`). Το `PESTO_APP_TOKEN` χρησιμοποιεί ξεχωριστό `device_key` (`"app_token"`) από τα tokens των συσκευών.

> **Απαίτηση single-worker `uvicorn`:** Το in-memory `PlanStore` (για τα `plan_id` μίας χρήσης) και ο per-device rate limiter κρατούν κατάσταση στη μνήμη της διεργασίας, οπότε ο `uvicorn` πρέπει να τρέχει με **έναν process (`--workers 1`)**.

Η ροή βασίζεται σε **Plan-Then-Execute + Action-Selector + Context-Minimization**:

1. **Βήμα `plan` (μόνο trusted είσοδος χρήστη):** βλέπει την τρέχουσα ερώτηση (`query`), προαιρετικά **έως 2** προηγούμενες ερωτήσεις του χρήστη (`previous_queries`, ≤300 χαρακτήρες η καθεμία — ποτέ απαντήσεις του βοηθού και ποτέ ερωτήσεις που κόπηκαν από `guard:*`), και το προηγούμενο δομημένο εργαλείο (`previous`). Επιστρέφει είτε:
   - άμεση απάντηση (`reply` ≤300 χαρακτήρες + προαιρετικό `navigate` enum),
   - **τοπικό action-selector** (`stat` ή `get_profiles`, με `plan_id: null`), που απαντιέται **100% τοπικά στο κινητό** από έτοιμα templates χωρίς δεύτερη κλήση LLM, είτε
   - **έως 3** read-only εργαλεία αναζήτησης (`search_history`, `recent_history`, `search_dictionary`) δεμένα με `plan_id` μίας χρήσης (TTL **60 s**).
2. **Βήμα `answer` (μόνο όταν εκδόθηκε `plan_id`):** εξαργυρώνει μία φορά το `plan_id` για την ίδια συσκευή και το ίδιο `query`, διαβάζει τα datamarked αποσπάσματα (`<tool_results>`, XML-escaped attributes συμπεριλαμβανομένων των εισαγωγικών) και επιστρέφει `reply` (≤300 χαρακτήρες) και τα `0-based` indices των σχετικών αποσπασμάτων (`relevant`: `0..2`), χωρίς εργαλεία και χωρίς `navigate`.

### Τι φεύγει από το κινητό

- **Ποτέ δεν φεύγουν:** πλήρη κείμενα υπαγορεύσεων, database IDs, πλήρες Λεξικό, αριθμητικά στατιστικά (`Totals`), ή ονόματα εφαρμογών από τα στατιστικά (`top_app`) και τις αναθέσεις προφίλ (`get_profiles`).
- **Στο `plan`:** μόνο το `query` (≤1.000 χαρακτήρες), έως 2 προηγούμενα `previous_queries` του χρήστη (≤300 χαρακτήρες), και το δομημένο `previous` (`tool`, `keywords`, `metric`, `period`).
- **Στο `answer` (μόνο σε αναζήτηση Ιστορικού/Λεξικού):** συνολικά **έως 3 αποσπάσματα** (`history_matches` + `recent_history` μαζί) των **≤160 χαρακτήρων** το καθένα (με καθαρισμό μεμονωμένων UTF-16 surrogates και κόψιμο σε όριο code point), ή **έως 5 όροι** του Λεξικού που ταιριάζουν στα `keywords`.
- **Logs (`backend/logs/metrics.jsonl`):** καταγράφονται αποκλειστικά αριθμοί και κωδικοί κατάστασης (χρόνος, tokens, πλήθος εργαλείων/citations), ποτέ κείμενο ερωτήσεων, αποσπασμάτων ή απαντήσεων.

| Απάντηση | Πότε |
|---|---|
| `200` | `reply` ή `tool_calls` |
| `403` `{"detail": "unauthorized"}` | Λείπει/λάθος/ανακληθέν `Bearer` token (ελέγχεται πριν από το body) |
| `409` `{"detail": "invalid_or_expired_plan"}` | Άγνωστο, ληγμένο (>60 s), ξαναχρησιμοποιημένο ή ξένης συσκευής `plan_id` |
| `413` `{"detail": "body_too_large"}` | Request body πάνω από 16 KB (`Content-Length` ή streaming) |
| `422` | Άγνωστο/μη έγκυρο πεδίο, >3 συνολικά snippets, ή `tool_results` εκτός του `plan_id` |
| `429` `{"detail": "rate_limited", "retry_after_s": …}` | Υπέρβαση ορίου συσκευής στο `plan` (`6/λεπτό`, `60/ώρα`, `200/ημέρα`, με `Retry-After`) |
| `503` `{"detail": "busy", "retry_after_s": 3}` | Κορεσμός ταυτόχρονων κλήσεων Haiku (`Semaphore(2)`, με `Retry-After`) |

### Παραδείγματα `POST /v1/assistant`

```json
{"type": "plan",
 "locale": "el",
 "query": "Τι είπα για το meeting;",
 "previous_queries": ["Πόσα λεπτά έχω κερδίσει;"],
 "previous": {"tool": "stat", "keywords": [], "metric": "minutes_saved", "period": "today"}}
```

```json
{"type": "tool_calls",
 "plan_id": "pX9_2kLm8vQr4tYz1wBc7nDf0gHj3sKp",
 "tools": [{"tool": "search_history", "keywords": ["meeting"], "period": "week"}]}
```

```json
{"type": "answer",
 "plan_id": "pX9_2kLm8vQr4tYz1wBc7nDf0gHj3sKp",
 "locale": "el",
 "query": "Τι είπα για το meeting;",
 "tool_results": {"history_matches": [{"created_at": "2026-10-30 18:26", "app": "Gmail", "profile": "mail", "words": 17, "snippet": "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη στις 10:00."}]}}
```

```json
{"type": "reply",
 "reply": "Την Πέμπτη έστειλες στο Gmail ότι το meeting πάει στις 10:00.",
 "navigate": {"destination": "HISTORY"},
 "relevant": [0]}
```
