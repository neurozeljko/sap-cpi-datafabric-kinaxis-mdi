# ADR-003: In-Flight Graph Cycle Detection and GraphQL Cursor Streaming for Supply Chain Data Fabric

## Status
Accepted

## Context
High-volume master data synchronisation between SAP S/4HANA and Kinaxis Maestro causes JVM Out-Of-Memory (OOM) failures when using monolithic payloads. Circular Bill-of-Material (BOM) references can destabilise supply chain planning solvers.

## Decision
1. **Delta Ingestion:** Consume SAP Master Data Integration (MDI) change events rather than executing bulk table extracts.
2. **In-Flight DFS Graph Traversal:** Execute a recursive Depth-First Search algorithm inside the Cloud Integration runtime to detect and quarantine circular component hierarchies before outbound transmission.
3. **GraphQL Cursor Streaming:** Dispatch cleansed records in micro-batches (250 items) via GraphQL mutations using cursor pagination tokens (`hasMore`, `endCursor`).
4. **Circuit Breaker:** Isolate runtime timeouts or schema corruptions to a quarantine Dead-Letter Queue while keeping the data streaming pipeline operational.
