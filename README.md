# Sistem distribuit de stocare a fișierelor (proiect Sisteme Distribuite).

## Membrii echipei

- Vint Alexandru

## Compilare

Necesita JDK 17+ si Maven.

```bash
mvn -q package
```

## Pornire

Fiecare componenta este un proces separat; porturile, directoarele si coordonatorul vin
din linia de comanda. Comenzile se dau din radacina repository-ului.

Noduri de stocare, cate un terminal pentru fiecare (`--coordinator` marcheaza
coordonatorul, pana la alegerea prin Bully din Etapa 5):

```bash
java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2
java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3
```

Client, in alt terminal:

```bash
java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001
```

Alternativ, toate cele 3 noduri dintr-un singur terminal (scriptul compileaza singur):

```bash
./scripts/start-local.sh
```

Iesire asteptata la Etapa 1:

```
[n2] pornit: port 7002, director data/n2, rol: nod de stocare
[client] coordonator configurat: localhost:7001; comenzi: put, get, ls, rm, nodes, exit
```

## Documentatie

Cerintele, modelul de date, obiectivele, scenariul demonstratiei si schita protocolului
sunt in [docs/cerinte.md](docs/cerinte.md).
