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

--

## 4. Scenariul demonstratiei

--

## 5. Schita protocolului

--
