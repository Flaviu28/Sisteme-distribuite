# Sisteme-distribuite

## Membrii echipei
- Salnicean Flaviu-Marian

## Compilare
```bash
mvn clean compile
```

## Pornire procese
Nodul 1 (Coordonator + Nod de stocare)
```bash
java -cp target/classes ro.ubm.sd.node.StorageNode -id n1 -port 7001 -dir data/n1 --coordinator
```
Nodul 2 (Nod de stocare)
```bash
java -cp target/classes ro.ubm.sd.node.StorageNode -id n2 -port 7002 -dir data/n2
```
Nodul 3 (Nod de stocare)
```bash
java -cp target/classes ro.ubm.sd.node.StorageNode -id n3 -port 7003 -dir data/n3
```
Clientul (Terminal interfață)
```bash
java -cp target/classes ro.ubm.sd.client.Client -coordinator localhost:7001
```