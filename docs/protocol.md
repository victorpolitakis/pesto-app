# Πρωτόκολλο υπαγόρευσης (κινητό ↔ backend)

Η ροή, η σειρά των μηνυμάτων και παραδείγματα. **Τα πεδία και οι τύποι τους ζουν μόνο στο [`backend/src/pesto_server/protocol.py`](../backend/src/pesto_server/protocol.py)**: αν κάτι εδώ διαφωνεί με εκείνο, ισχύει εκείνο.

## Σύνδεση

```
ws://<host>:8000/v1/dictate
Authorization: Bearer <PESTO_APP_TOKEN>
```

- Το token είναι στο `backend/.env`. Αν το header δεν βολεύει, γίνεται δεκτό και `?token=<PESTO_APP_TOKEN>`.
- Χωρίς σωστό token ο server κλείνει τη σύνδεση πριν από το handshake με κωδικό **1008**.
- Από προεπιλογή ο server ακούει στο `127.0.0.1`. Για το κινητό στο ίδιο Wi-Fi: `HOST=0.0.0.0` στο `.env`. Ο emulator βλέπει το laptop στο `10.0.2.2`.
- Μία υπαγόρευση = μία σύνδεση. Μετά το `final` (ή το `cancel`) ο server κλείνει τη σύνδεση.

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
