# Sisteme-distribuite - Cerințe și Protocol

## Schița protocolului (Pasul 6)

| Mesaj | De la → către | Conținut | Răspuns |
| :--- | :--- | :--- | :--- |
| ALLOCATE | client → coordonator | nume, dimensiune | NODES cu ID-ul fișierului și 2 noduri, sau ERROR |
| PUT | client → nod | ID, nume, dimensiune, checksum, nodul pentru copie + conținut fișier | STORED sau ERROR |
| REPLICATE | nod → nod | ca PUT, fără nodul următor + conținut fișier | STORED sau ERROR |
| COMMIT | nod → coordonator | ID fișier, nodurile care îl dețin | OK |
| LOCATE | client → coordonator | nume sau ID | LOCATIONS cu metadatele, sau NOT_FOUND |
| GET | client → nod | ID fișier | antet cu dimensiunea + conținut fișier, sau NOT_FOUND |
| LIST_FILES | client → coordonator | - | Listă fișiere (ID, nume, dimensiune, checksum, noduri) |
| DELETE | client → coordonator | nume sau ID fișier | NODES_TO_DELETE cu nodurile de curățat, sau NOT_FOUND |
| DELETE_FILE | coordonator → nod | ID fișier | DELETED sau ERROR |
| LIST_NODES | client → coordonator | - | Listă noduri active, starea lor și cine este coordonator |
