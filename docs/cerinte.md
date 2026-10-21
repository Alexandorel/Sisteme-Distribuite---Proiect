# Cerinte - Sistem distribuit de stocare a fisierelor (Etapa 1)

Sistem care stocheaza fisiere pe mai multe noduri: fiecare fisier are doua copii pe
noduri distincte si ramane accesibil cand un nod cade. Metadatele trec prin coordonator,
iar continutul fisierelor circula direct intre client si noduri (ca in HDFS).

## Decizii generale

- **Identificarea fisierului:** utilizatorul lucreaza cu **numele** fisierului
  (ex. `raport.pdf`). Numele este unic in sistem. Fiecare fisier primeste si un
  **UUID** intern, folosit de coordonator pentru metadate si pentru urmarirea copiilor.
- **`put` pe un nume existent:** este **refuzat cu eroare**; nimic nu se suprascrie,
  pentru a evita pierderea accidentala de date.
- **Dimensiune maxima per fisier:** **1 GB**. Peste aceasta limita, `put` este refuzat.
- **Alegerea nodurilor pentru o copie noua:** coordonatorul alege cele 2 noduri ACTIVE
  cu cele mai putine fisiere stocate; la egalitate, in ordinea ID-ului. Astfel un nod
  adaugat la rulare (fara fisiere) primeste primele incarcari noi.

## 1. Cerinte functionale - operatiile

Fiecare operatie: comanda, pasii prin sistem, rezultatul si erorile tratate.

| Operatie | Comanda | Pasii prin sistem | Rezultat | Erori tratate |
|---|---|---|---|---|
| Incarcare | `put raport.pdf` | 1. clientul cere coordonatorului doua noduri active, distincte (ALLOCATE); 2. trimite fisierul primului nod (PUT); 3. primul nod il copiaza pe al doilea (REPLICATE); 4. dupa ambele confirmari, coordonatorul inregistreaza metadatele (COMMIT) | ID-ul fisierului si cele doua noduri care il detin | mai putin de 2 noduri active: refuz, nimic stocat · fisier local inexistent · nume deja existent: refuz · fisier > 1 GB: refuz · o copie esuata: se alege alt nod |
| Descarcare | `get raport.pdf ./out/` | 1. clientul cere coordonatorului locatiile copiilor (LOCATE); 2. descarca de la primul nod (GET); 3. verifica checksum-ul | fisierul salvat local, cu checksum verificat | fisier necunoscut · primul nod nu raspunde sau checksum gresit: incearca a doua copie · nicio copie accesibila: eroare explicita |
| Listare | `ls` | 1. clientul cere coordonatorului lista fisierelor (LIST); 2. coordonatorul raspunde din tabela de metadate | pentru fiecare fisier: nume, dimensiune, nodurile care il detin | coordonator inaccesibil: eroare de conexiune · niciun fisier: lista goala (nu e eroare) |
| Stergere | `rm raport.pdf` | 1. clientul cere coordonatorului stergerea (DELETE); 2. coordonatorul ordona stergerea nodurilor active care detin copii; 3. sterge metadatele | fisierul eliminat de pe noduri si din metadate | fisier necunoscut: eroare · un nod cu o copie este cazut: copia de pe nodul activ se sterge si metadatele se actualizeaza; stergerea ramane in asteptare pentru nodul cazut si se aplica la revenirea lui, ca sa nu ramana o copie orfana |
| Starea sistemului | `nodes` | 1. clientul cere coordonatorului starea (STATUS); 2. coordonatorul raspunde cu lista nodurilor cunoscute | pentru fiecare nod: ID, adresa:port, stare (ACTIV/SUSPECT/CAZUT) si cine este coordonator | coordonator inaccesibil: eroare de conexiune |
| Adaugare nod la rulare | (nodul porneste cu `--id/--port/--dir`) | 1. nodul nou se anunta prin descoperire (Etapa 2); 2. coordonatorul il adauga in lista cu starea ACTIV; 3. devine candidat pentru copii noi (Etapa 4) | nodul apare in `nodes`; urmatoarele incarcari il pot folosi | nodul nu este descoperit: ramane in afara sistemului, fara efect asupra celor existente; copiile existente nu se redistribuie, doar cele noi |

## 2. Modelul de date

Coordonatorul tine doua tabele in memorie: metadatele fisierelor si lista nodurilor.

### Metadatele unui fisier

| Camp | Tip Java | Exemplu | Stabilit de |
|---|---|---|---|
| identificator | `UUID` | `3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90` | coordonator, la incarcare |
| nume | `String` | `raport.pdf` | utilizator |
| dimensiune | `long` (octeti) | `482133` | client |
| checksum | `String` (SHA-256, hex) | `9f86d081...` | client, verificat de fiecare nod |
| locatiile copiilor | `List<String>` (ID-uri de nod) | `[n2, n3]` | coordonator, dupa confirmari |

Checksum-ul se calculeaza cu `MessageDigest.getInstance("SHA-256")` din JDK. El
dovedeste ca fisierul descarcat este identic cu cel incarcat, indiferent de nodul care
l-a servit.

### Un nod din lista

| Camp | Tip Java | Exemplu |
|---|---|---|
| ID nod | `String` | `n2` |
| adresa si port | `String`, `int` | `192.168.37.102`, `7002` |
| stare | `enum` | `ACTIV`, `SUSPECT`, `CAZUT` (folosit din Etapa 3) |
| ultimul semn de viata | `long` (ms) | `System.currentTimeMillis()` la ultimul heartbeat |

### Reconstructia metadatelor

Metadatele traiesc in memoria coordonatorului; daca el cade, dispar odata cu el. De aceea
fiecare nod poate raporta ce fisiere detine, astfel incat noul coordonator sa reconstruiasca tabela de metadate din rapoartele nodurilor.

## 3. Cerinte nefunctionale si obiective masurabile

Fiecare obiectiv are un test care il verifica; valorile sunt alese de echipa si
justificate mai jos.

| Cerinta | Obiectiv masurabil | Cum se masoara | Etapa |
|---|---|---|---|
| Integritate | 0 fisiere cu checksum diferit din 100 de descarcari | script care incarca 100 de fisiere (1 KB - 10 MB, continut aleator), le descarca si compara SHA-256 | 2 |
| Descoperire | un nod nou apare in lista celorlalti in cel mult 3 s | diferenta dintre timestamp-ul de pornire al nodului si cel de "nod descoperit" din logurile celorlalti | 2 |
| Detectarea defectelor | un nod oprit cu `kill -9` este marcat CAZUT in cel mult 5 s | diferenta dintre momentul `kill -9` si timestamp-ul "nod cazut" din logurile coordonatorului | 3 |
| Scalabilitate | debitul total de incarcare cu 4 noduri este de cel putin 1,5 ori cel cu 2 noduri | 10 fisiere de 100 MB incarcate de 2 clienti in paralel; timpul total, in ambele configuratii | 4 |
| Coordonare | noul coordonator este ales in cel mult 10 s dupa caderea celui vechi | diferenta dintre momentul `kill -9` si timestamp-ul "coordonator nou" din logurile Bully | 5 |
| Replicare | fiecare fisier are exact 2 copii, pe noduri distincte | `ls` arata 2 noduri distincte per fisier; `sha256sum data/*/*` gaseste fiecare fisier in exact 2 directoare, cu checksum identic | 6 |
| Disponibilitate | dupa oprirea oricarui nod (inclusiv coordonatorul), 100% din fisiere raman descarcabile | se incarca 20 de fisiere, se opreste un nod cu `kill -9`, se descarca toate si se verifica checksum-ul; se repeta pentru fiecare nod | 7 |

### Justificarea valorilor

- **Integritate - 0 din 100:** orice fisier corupt inseamna pierdere de date, deci singura
  tinta acceptabila este zero; 100 de fisiere de dimensiuni diferite acopera si cazurile
  in care fisierul se imparte in mai multe pachete TCP.
- **Descoperire - 3 s:** nodurile se anunta prin UDP multicast la fiecare 1 s; UDP poate
  pierde pachete, iar 3 s permit pierderea a doua anunturi consecutive.
- **Detectare - 5 s:** heartbeat la fiecare 1 s; dupa 3 s fara semn de viata nodul devine
  SUSPECT, dupa 5 s CAZUT. Un prag mai mic ar marca drept cazut un nod doar incetinit.
- **Scalabilitate - 1,5x de la 2 la 4 noduri:** comparam cu 2 noduri, nu cu 1, pentru ca
  sub 2 noduri active `put` este refuzat. Cu 2 noduri fiecare fisier ajunge pe ambele;
  cu 4, incarcarea se imparte. Tinta nu este 2x, pentru ca la rularea pe aceeasi masina
  nodurile impart discul si procesorul.
- **Coordonare - 10 s:** detectarea caderii coordonatorului dureaza pana la 5 s, iar
  Bully mai are nevoie de cateva runde de mesaje, fiecare cu timeout de cel mult 1-2 s.
- **Replicare - exact 2 copii:** o copie nu supravietuieste caderii unui nod; trei copii
  dubleaza costul fara castig in ipoteza de mai jos (cel mult un nod cazut).
- **Disponibilitate - 100%:** cu 2 copii pe noduri distincte si cel mult un nod cazut,
  fiecare fisier are mereu o copie accesibila, deci orice valoare sub 100% este un defect.

### Ipoteze de lucru

Obiectivele de mai sus sunt valabile doar in interiorul acestor ipoteze:

- nodurile ruleaza in aceeasi retea locala, unde UDP multicast functioneaza;
- cel mult un nod este cazut la un moment dat, iar urmatoarea cadere apare abia dupa ce
  sistemul a detectat-o pe cea anterioara;
- defectele sunt de tip crash: un nod fie functioneaza corect, fie se opreste; nu trimite
  date gresite intentionat (nu sunt defecte bizantine);
- reteaua nu se partitioneaza: daca un nod nu raspunde, este considerat cazut;
- discul unui nod nu se corupe in timpul rularii; datele scrise raman citibile.

## 4. Scenariul demonstratiei

Scenariul este testul de acceptanta al proiectului: se ruleaza partial la fiecare etapa
si complet in saptamana 14. Toate comenzile se dau din radacina repository-ului, pe Linux.

### Pregatire

```bash
mvn -q package
rm -rf data/ out/ && mkdir out
head -c 482133 /dev/urandom > raport.pdf     # fisierul de test
head -c 100000 /dev/urandom > notite.txt      # al doilea fisier, pentru pasul 4
```

Terminalele folosite: T1-T4 pentru nodurile n1-n4, T5 pentru client, T6 pentru
comenzile operatorului (`kill`, `sha256sum`). Comenzile de pornire:

```bash
# T1
java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
# T2
java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2
# T3
java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3
# T5
java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001
```

In pasii de mai jos, `nX` este unul dintre cele doua noduri afisate de `put raport.pdf`
la pasul 2 (de exemplu `n2`).

### Pasii

| Pas | Actiunea operatorului | Comanda exacta | Ce trebuie sa se vada | Functioneaza din etapa |
|---|---|---|---|---|
| 1 | porneste nodurile n1, n2, n3 in terminale separate, apoi clientul | comenzile din T1, T2, T3, T5 de mai sus; in client: `nodes` | fiecare nod logheaza descoperirea celorlalti doi; `nodes` arata n1, n2, n3 ACTIV, n1 coordonator | 2 |
| 2 | incarca fisierul din client | T5: `put raport.pdf` | ID-ul fisierului (UUID) si doua noduri distincte care il detin, de ex. `[n2, n3]` | 2 |
| 3 | descarca fisierul si compara checksum-ul | T5: `get raport.pdf ./out/`<br>T6: `sha256sum raport.pdf out/raport.pdf` | clientul raporteaza checksum verificat; cele doua valori SHA-256 sunt identice | 2 |
| 4 | porneste nodul n4 fara a opri ceva, apoi incarca un fisier nou | T4: `java -cp target/classes ro.ubm.sd.node.StorageNode --id n4 --port 7004 --dir data/n4`<br>T5: `nodes`, apoi `put notite.txt` | n4 apare in `nodes` ca ACTIV in cel mult 3 s; `put notite.txt` il alege pe n4 (are cele mai putine fisiere) | 4 |
| 5 | opreste brusc un nod care detine `raport.pdf`, apoi descarca fisierul | T6: `pkill -9 -f "StorageNode --id nX"`<br>T5: `nodes`, apoi `get raport.pdf ./out/` | in cel mult 5 s `nodes` arata nX CAZUT; `get` reuseste din cealalta copie, cu checksum verificat | 3 |
| 5b | reporneste nodul oprit, ca sa ramana cel mult un nod cazut (ipoteza din sectiunea 3) | in terminalul lui nX: aceeasi comanda de pornire ca la inceput | nX revine ACTIV in `nodes`; pastreaza fisierele din `data/nX` | 3 |
| 6 | opreste brusc coordonatorul | T6: `pkill -9 -f "StorageNode --id n1"`<br>T5: `nodes` | nodurile ramase logheaza alegerea Bully; in cel mult 10 s apare un coordonator nou (nodul cu ID-ul cel mai mare, n4) | 5 |
| 7 | listeaza si descarca dupa realegere | T5: `ls`, apoi `get raport.pdf ./out/`<br>T6: `sha256sum raport.pdf out/raport.pdf` | `ls` arata `raport.pdf` si `notite.txt` cu dimensiunea si cele 2 noduri ale fiecaruia (metadate reconstruite din rapoartele nodurilor); checksum-urile sunt identice | 6 |

Observatii:

- La Etapa 1 clientul primeste coordonatorul prin `--coordinator localhost:7001`. De la
  Etapa 2 il gaseste prin descoperire, deci dupa pasul 6 clientul se conecteaza singur la
  noul coordonator, fara repornire.
- La pasul 7, daca n1 detinea o copie, fisierul ramane accesibil din cealalta copie; daca
  n1 nu detinea copii, ambele copii sunt accesibile.

## 5. Schita protocolului

Aici sunt doar mesajele si continutul lor; formatul exact si implementarea vin in Etapa 2.
TCP transporta un flux de octeti, deci fiecare mesaj are un **antet** - o linie JSON
terminata cu `\n` - iar daca are continut de fisier, acesta urmeaza imediat dupa antet,
ca exact `dimensiune` octeti. Exemplu de antet:

```
{"tip":"PUT","id":"3f2a9c1e-...","nume":"raport.pdf","dimensiune":482133,"checksum":"9f86d081...","urmator":"n3"}
```

### Incarcare si descarcare

| Mesaj | De la -> catre | Continut | Raspuns |
|---|---|---|---|
| ALLOCATE | client -> coordonator | nume, dimensiune | NODES cu ID-ul fisierului si 2 noduri (ID, adresa, port), sau ERROR (nume existent, fisier > 1 GB, mai putin de 2 noduri active) |
| PUT | client -> nod | ID, nume, dimensiune, checksum, nodul pentru copie + octetii fisierului | STORED sau ERROR (checksum diferit, eroare de disc, copia esuata) |
| REPLICATE | nod -> nod | ca PUT, fara nodul urmator | STORED sau ERROR |
| COMMIT | nod -> coordonator | ID fisier, nume, dimensiune, checksum, nodurile care il detin | OK |
| LOCATE | client -> coordonator | nume sau ID | LOCATIONS cu metadatele si nodurile (ID, adresa, port), sau NOT_FOUND |
| GET | client -> nod | ID fisier | antet cu dimensiunea + octetii fisierului, sau NOT_FOUND |

### Listare, stergere, starea sistemului

| Mesaj | De la -> catre | Continut | Raspuns |
|---|---|---|---|
| LIST | client -> coordonator | - | FILES: pentru fiecare fisier nume, ID, dimensiune, nodurile care il detin (lista poate fi goala) |
| DELETE | client -> coordonator | nume | DELETED cu nodurile de pe care s-a sters si cele cu stergere in asteptare, sau NOT_FOUND |
| REMOVE | coordonator -> nod | ID fisier | REMOVED sau NOT_FOUND (copia lipsea deja; tratat tot ca succes) |
| STATUS | client -> coordonator | - | NODE_LIST: pentru fiecare nod ID, adresa, port, stare (ACTIV/SUSPECT/CAZUT) si ID-ul coordonatorului |

La `rm`, coordonatorul trimite REMOVE doar nodurilor active si sterge imediat metadatele.
Pentru nodurile cazute pastreaza ID-ul fisierului intr-o lista de stergeri in asteptare
si trimite REMOVE cand nodul revine (vezi REPORT).

### Reconstructia metadatelor

| Mesaj | De la -> catre | Continut | Raspuns |
|---|---|---|---|
| REPORT | nod -> coordonator | ID-ul nodului si, pentru fiecare fisier detinut: ID, nume, dimensiune, checksum | OK |

Fiecare nod trimite REPORT cand intra in sistem sau cand apare un coordonator nou.
Coordonatorul reconstruieste tabela de metadate din rapoarte: locatiile unui fisier sunt
nodurile care il raporteaza. Pentru un fisier aflat in lista de stergeri in asteptare,
raspunde cu REMOVE in loc sa-l inregistreze.

Mesajele de descoperire (Etapa 2), heartbeat (Etapa 3) si alegere a coordonatorului
(Etapa 5) se adauga in etapele respective.
