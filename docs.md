# IPFlowMetrics Write Definition

## Überblick

Das Java-Programm schreibt pro verarbeitetem Flow genau **eine Zeile** in die MySQL-Tabelle `IPFlowMetrics`.

Dabei gilt jetzt:

- **IPv4-Quelladresse** wird in `fromIPv4` geschrieben
- **IPv6-Quelladresse** wird in `fromIPv6` geschrieben
- **IPv4-Zieladresse** wird in `toIPv4` geschrieben
- **IPv6-Zieladresse** wird in `toIPv6` geschrieben
- **IPv4-Next-Hop** wird in `nextHopIPv4` geschrieben
- **IPv6-Next-Hop** wird in `nextHopIPv6` geschrieben

Wenn ein Feld im empfangenen Flow **nicht vorhanden** ist, wird die jeweilige Spalte als `NULL` gespeichert.

---

## Exaktes Mapping in die Tabelle `IPFlowMetrics`

### Adressen

| DB-Spalte | Quelle aus NetFlow | Typ im Java-Code | Verhalten |
|---|---|---:|---|
| `fromIPv4` | `FlowField.IPV4_SRC_ADDR` | `Long` | wird gesetzt, wenn 4-Byte IPv4 vorhanden |
| `fromIPv6` | `FlowField.IPV6_SRC_ADDR` | `byte[16]` | wird gesetzt, wenn 16-Byte IPv6 vorhanden |
| `toIPv4` | `FlowField.IPV4_DST_ADDR` | `Long` | wird gesetzt, wenn 4-Byte IPv4 vorhanden |
| `toIPv6` | `FlowField.IPV6_DST_ADDR` | `byte[16]` | wird gesetzt, wenn 16-Byte IPv6 vorhanden |
| `nextHopIPv4` | `FlowField.IPV4_NEXT_HOP` | `Long` | wird gesetzt, wenn 4-Byte IPv4 vorhanden |
| `nextHopIPv6` | `FlowField.IPV6_NEXT_HOP` | `byte[16]` | wird gesetzt, wenn 16-Byte IPv6 vorhanden |

### Metriken / Transportdaten

| DB-Spalte | Quelle aus NetFlow | Typ im Java-Code | Verhalten |
|---|---|---:|---|
| `byteSize` | `FlowField.IN_BYTES` | `long` | fallback `0`, wenn nicht vorhanden |
| `protocol` | `FlowField.PROTOCOL` | `int` | fallback `0`, wenn nicht vorhanden |
| `srcPort` | `FlowField.L4_SRC_PORT` | `int` | fallback `0`, wenn nicht vorhanden |
| `dstPort` | `FlowField.L4_DST_PORT` | `int` | fallback `0`, wenn nicht vorhanden |
| `pkts` | `FlowField.IN_PKTS` | `int` | aus `u32`, bei Overflow auf `Integer.MAX_VALUE` begrenzt |

### Automatisch durch DB

| DB-Spalte | Herkunft |
|---|---|
| `id` | `autoincrement()` |
| `timestamp` | `@default(now())` in MySQL |

---

## Aktuelle Logik für IPv4 und IPv6

### Fall 1: Reiner IPv4-Flow
Dann wird geschrieben:

- `fromIPv4` = gesetzt
- `toIPv4` = gesetzt
- `nextHopIPv4` = gesetzt oder `NULL`
- `fromIPv6` = `NULL`
- `toIPv6` = `NULL`
- `nextHopIPv6` = `NULL`

### Fall 2: Reiner IPv6-Flow
Dann wird geschrieben:

- `fromIPv6` = gesetzt
- `toIPv6` = gesetzt
- `nextHopIPv6` = gesetzt oder `NULL`
- `fromIPv4` = `NULL`
- `toIPv4` = `NULL`
- `nextHopIPv4` = `NULL`

### Fall 3: Exporter liefert beides
Dann können **beide** Spaltenpaare gesetzt sein:

- `fromIPv4` und/oder `fromIPv6`
- `toIPv4` und/oder `toIPv6`
- `nextHopIPv4` und/oder `nextHopIPv6`

Das System erzwingt **keine Exklusivität**. Es schreibt einfach alles, was im Flow korrekt vorhanden ist.

---

## Technisch: Wo das passiert

### 1. Decoding
In `FlowDecoder.decode(...)`:

- liest IPv4:
  - `IPV4_SRC_ADDR`
  - `IPV4_DST_ADDR`
  - `IPV4_NEXT_HOP`
- liest IPv6:
  - `IPV6_SRC_ADDR`
  - `IPV6_DST_ADDR`
  - `IPV6_NEXT_HOP`
- liest zusätzlich:
  - `IN_BYTES`
  - `IN_PKTS`
  - `PROTOCOL`
  - `L4_SRC_PORT`
  - `L4_DST_PORT`

IPv6 wird nur akzeptiert, wenn die Länge **genau 16 Byte** ist.

### 2. Direkte DB-Row
Der JSON-/Event-Zwischenschritt wurde entfernt. `FlowDecoder.decode(...)` erzeugt direkt ein `IpFlowMetricsRepository.Row`.

Die aktuelle Row-Definition ist:

- `Long fromIPv4`
- `byte[] fromIPv6`
- `Long toIPv4`
- `byte[] toIPv6`
- `long byteSize`
- `Long nextHopIPv4`
- `byte[] nextHopIPv6`
- `int protocol`
- `int srcPort`
- `int dstPort`
- `int pkts`

### 3. Pufferung und DB-Schreiben
In `Collector.handleFlow(...)` wird die vom Decoder erzeugte `Row` direkt an `AsyncDbWriter.offer(...)` übergeben.

`AsyncDbWriter`:

- puffert Rows in einer bounded Queue
- sammelt Batches im Hintergrundthread
- schreibt die gesammelten Rows standardmaessig alle 500ms als JDBC-Batch
- ruft `IpFlowMetricsRepository.insertBatch(...)` auf

### 4. JDBC-Binding
Im `PreparedStatement` wird gebunden als:

1. `fromIPv4`
2. `fromIPv6`
3. `toIPv4`
4. `toIPv6`
5. `byteSize`
6. `nextHopIPv4`
7. `nextHopIPv6`
8. `protocol`
9. `srcPort`
10. `dstPort`
11. `pkts`

Für IPv6 gilt:

- wenn `byte[] == null` oder Länge ungleich `16` -> SQL `NULL`
- sonst `statement.setBytes(...)`

---

## Kurzdefinition in einem Satz

**Neue Definition:**  
Das Programm liest NetFlow, dekodiert die relevanten IPv4-/IPv6-/Transportfelder direkt in eine DB-Row, puffert diese im Speicher und schreibt sie per JDBC-Batch über HikariCP nach MySQL.

Standardverhalten: Die gepufferten Rows werden alle 500ms als Batch nach MySQL geschrieben; bei Erreichen von `batchSize` wird auch frueher geflusht.


