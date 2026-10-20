# SD Storage - Cerințe, Model și Protocol (Etapa 1)

## Pasul 2 - Cerințe funcționale: operațiile

| Operație | Comanda | Pașii prin sistem | Rezultat | Erori tratate |
| :--- | :--- | :--- | :--- | :--- |
| **Încărcare** | `put <cale_fisier>` | 1. Clientul cere coordonatorului două noduri active distincte.<br>2. Trimite fișierul primului nod.<br>3. Primul nod îl copiază pe al doilea.<br>4. După ambele confirmări, coordonatorul înregistrează metadatele. | ID-ul fișierului și cele două noduri pe care a fost stocat. | Mai puțin de 2 noduri active (refuz, nimic stocat); fișier local inexistent; o copie eșuată (se alege alt nod). |
| **Descărcare** | `get <nume/id> <cale_dest>` | 1. Clientul cere coordonatorului locațiile copiilor.<br>2. Descarcă de la primul nod.<br>3. Verifică checksum-ul SHA-256 local. | Fișier salvat local, cu integritatea confirmată prin checksum. | Fișier necunoscut; primul nod nu răspunde sau checksum greșit (se încearcă a doua copie); nicio copie accesibilă. |
| **Listare fișiere** | `ls` | 1. Clientul trimite cerere către coordonator.<br>2. Coordonatorul întoarce metadatele tuturor fișierelor din memorie. | Tabel cu fișierele: nume, ID, dimensiune și nodurile care dețin replicile. | Coordonatorul este inaccesibil (timeout/eroare conexiune). |
| **Ștergere** | `rm <nume/id>` | 1. Clientul cere ștergerea la coordonator.<br>2. Coordonatorul identifică nodurile și le comandă ștergerea locală.<br>3. Coordonatorul elimină metadatele. | Confirmare de ștergere reușită. | Fișier inexistent. Dacă un nod este căzut, coordonatorul marchează ștergerea ca restantă (pending) pentru când nodul revine. |
| **Stare sistem** | `nodes` | 1. Clientul cere lista nodurilor de la coordonator.<br>2. Coordonatorul trimite lista cu starea curentă a fiecărui nod. | Lista nodurilor cunoscute: ID, adresă, port, stare și marcajul de coordonator. | Coordonatorul este căzut. |
| **Adăugare nod nou** | *(eveniment de sistem)* | 1. Nodul nou pornește și trimite mesaj de înregistrare.<br>2. Coordonatorul îl adaugă în tabela de noduri active.<br>3. Nodul devine eligibil pentru noi operații de `put`. | Nodul apare cu starea `ACTIV` în lista de noduri fără întreruperea serviciului. | Conflict de ID sau port deja utilizat. |

### Decizii de proiectare:
* **Identificare fișiere:** Fiecare fișier primește un UUID unic la încărcare; utilizatorul se poate referi la fișier prin nume sau prin UUID.
* **Nume duplicat:** La încărcarea unui fișier cu un nume deja existent, operația este respinsă cu mesaj de eroare (suprascrierea nu este permisă implicit).
* **Dimensiune maximă:** Dimensiunea maximă a unui fișier suportat în această etapă este de 500 MB.

---

## Pasul 3 - Modelul de date

### 1. Metadatele unui fișier (gestionate de coordonator în memorie)
* **`id`** (`UUID`): Identificator unic generat de coordonator la comanda `put`.
* **`nume`** (`String`): Numele original al fișierului specificat de utilizator.
* **`dimensiune`** (`long`): Mărimea în octeți, stabilită de client.
* **`checksum`** (`String`): Valoare SHA-256 calculată de client și verificată de fiecare nod de stocare.
* **`noduri`** (`List<String>`): ID-urile celor două noduri care dețin replicile, stabilite după confirmarea salvării.

### 2. Informațiile despre un nod
* **`id`** (`String`): Identificatorul nodului (ex: `n1`, `n2`).
* **`adresa`** (`String`): Adresa IP a nodului (ex: `localhost`, `192.168.1.10`).
* **`port`** (`int`): Portul TCP de comunicație (ex: `7001`, `7002`).
* **`stare`** (`enum`): `ACTIV`, `SUSPECT`, `CAZUT`.
* **`lastHeartbeat`** (`long`): Timestamp în milisecunde (`System.currentTimeMillis()`) al ultimului semnal de viață.

---

## Pasul 4 - Cerințe nefuncționale și obiective măsurabile

* **Ipoteze de lucru:** 
  - Comunicația se desfășoară în rețea locală fiabilă.
  - Defectele nodurilor sunt doar de tip crash-stop (fără comportamente bizantine).
  - Cel mult un nod cade la un moment dat în timpul demonstrației.

| Cerință | Obiectiv măsurabil | Cum se măsoară | Etapa |
| :--- | :--- | :--- | :--- |
| **Integritate** | 0 fișiere corupte din 100 de descărcări | Script automat care uploadează și downloadează 100 de fișiere, comparând SHA-256. | 2 |
| **Descoperire** | Un nod nou apare în sistem în cel mult 3 secunde | Diferența de timp între timestamp-ul de start al nodului și timestamp-ul recepției la coordonator. | 2 |
| **Detectare defecte**| Un nod oprit forțat (`kill -9`) este marcat `CAZUT` în cel mult 5 secunde | Analiza logurilor coordonatorului cu timestamp după emiterea semnalului `kill -9`. | 3 |
| **Scalabilitate** | Debitul de încărcare cu 3 noduri crește față de cel cu 1 nod | Transfer simultan a 10 fișiere de 100 MB și compararea timpului total de execuție. | 4 |
| **Coordonare** | Noul lider este ales în cel mult 10 secunde de la căderea coordonatorului | Analiza logurilor algoritmului Bully de la cădere până la anunțul noului coordonator. | 5 |
| **Replicare** | Fiecare fișier are exact 2 copii pe noduri distincte | Verificarea comenzii `ls` și a fișierelor fizice prezente în directoarele `data/`. | 6 |
| **Disponibilitate** | 100% din fișiere descărcabile după oprirea unui nod de stocare | Descărcarea fișierelor stocate după ce un nod cu replici a primit `kill -9`. | 7 |

---

## Pasul 5 - Scenariul demonstrației

| Pas | Acțiunea operatorului | Comanda executată | Ce trebuie să se vadă | Funcționează din |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Pornire noduri în terminale separate | `java -cp target/classes ro.ubm.sd.node.StorageNode -id n1 -port 7001 -dir data/n1 --coordinator`<br>`java -cp target/classes ro.ubm.sd.node.StorageNode -id n2 -port 7002 -dir data/n2`<br>`java -cp target/classes ro.ubm.sd.node.StorageNode -id n3 -port 7003 -dir data/n3` | Fiecare nod afișează că este pornit; `n1` își asumă rolul de coordonator. | Etapa 2 |
| 2 | Încărcare fișier din client | `put raport.pdf` | Coordonatorul alocă 2 noduri distincte (ex: `n2`, `n3`), fișierul este transferat, se afișează ID-ul generat. | Etapa 2 |
| 3 | Descărcare fișier | `get raport.pdf ./out/` | Fișierul se descarcă cu succes, iar SHA-256 coincide cu cel inițial. | Etapa 2 |
| 4 | Adăugare nod dinamic la rulare | `java -cp target/classes ro.ubm.sd.node.StorageNode -id n4 -port 7004 -dir data/n4` | Nodul `n4` este descoperit automat și apare la interogarea `nodes`. | Etapa 4 |
| 5 | Oprire forțată a unui nod de stocare | `kill -9 <PID_n2>` | Nodul `n2` este detectat căzut; comanda `get raport.pdf` funcționează servind din a doua copie (`n3`). | Etapa 3 |
| 6 | Oprire forțată coordonator | `kill -9 <PID_n1>` | Nodurile active detectează lipsa coordonatorului și aleg un nou coordonator prin algoritmul Bully. | Etapa 5 |
| 7 | Verificare după realegere | `ls` și `get raport.pdf` | Noul coordonator a reconstruit tabela de metadate din rapoartele nodurilor; fișierele sunt accesibile. | Etapa 6 |

---

## Pasul 6 - Schița protocolului

| Mesaj | De la → către | Conținut | Răspuns |
| :--- | :--- | :--- | :--- |
| **ALLOCATE** | client → coordonator | `nume, dimensiune` | `NODES` cu ID-ul fișierului și 2 noduri, sau `ERROR` |
| **PUT** | client → nod | `ID, nume, dimensiune, checksum, nodul pentru copie` + octeți fișier | `STORED` sau `ERROR` |
| **REPLICATE** | nod → nod | ca `PUT`, fără nodul următor + octeți fișier | `STORED` sau `ERROR` |
| **COMMIT** | nod → coordonator | `ID fișier, nodurile care îl dețin` | `OK` |
| **LOCATE** | client → coordonator | `nume` sau `ID` | `LOCATIONS` cu metadatele, sau `NOT_FOUND` |
| **GET** | client → nod | `ID fișier` | antet cu dimensiunea + conținut fișier, sau `NOT_FOUND` |
| **LIST_FILES** | client → coordonator | *(fără conținut)* | Listă fișiere: `ID, nume, dimensiune, checksum, [noduri]` |
| **DELETE** | client → coordonator | `nume` sau `ID fișier` | `NODES_TO_DELETE` cu nodurile de curățat, sau `NOT_FOUND` |
| **DELETE_FILE**| coordonator → nod | `ID fișier` | `DELETED` sau `ERROR` |
| **LIST_NODES** | client → coordonator | *(fără conținut)* | Listă noduri active, adrese, porturi, stare și rol |
