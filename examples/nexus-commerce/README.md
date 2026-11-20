# Nexus Commerce

**Status:** Skeleton — Implementation begins at Milestone M10

---

## Overview

Nexus Commerce is the reference enterprise application for GraphORM. It is a realistic commerce and supply-chain system used to validate GraphORM against complex relationships, queries, transactions, and concurrent workloads.

This application is **not** a simple CRUD demo. It is designed to test the full capability of GraphORM under production-like conditions.

---

## Domains

- Organizations and access control
- Product catalog (products, variants, categories, pricing)
- Warehouses and inventory management
- Suppliers and procurement
- Customers and orders
- Payments and refunds
- Shipments and returns
- Promotions and loyalty
- Analytics and reporting

---

## Development Schedule

This module is populated during **Milestone M10 — Nexus Commerce Validation**.

Prerequisites before M10:
- M7 (Spring Boot Starter and CLI) — provides the application framework
- M8 (Advanced PostgreSQL Features) — required for CTEs, window functions, JSONB
- M9 (Transactions, Concurrency) — required for inventory reservation and concurrent purchasing

---

## Running (after M10 implementation)

```bash
mvn spring-boot:run -pl examples/nexus-commerce
```

Requires Docker (for Testcontainers) or a local PostgreSQL instance.

---

## Performance Documentation

After M10, performance measurements will be in [PERFORMANCE.md](PERFORMANCE.md).

## Known Limitations

After M10, known limitations will be documented in [LIMITATIONS.md](LIMITATIONS.md).
