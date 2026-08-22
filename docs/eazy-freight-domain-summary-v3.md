# Eazy Freight Inc — Domain Overview
### Workshop Reference Document v3

---

## The Business Problem

The global freight forwarding industry has a massive long tail. A handful of large forwarders — Kuehne+Nagel, DB Schenker, Flexport — run expensive enterprise platforms costing tens of thousands of dollars per year. Below them sit thousands of small independent freight forwarding companies, typically 5 to 50 employees, running their entire business on:

- **Excel spreadsheets** — rate quotes, shipment tracking, container lists
- **Email threads** — booking confirmations, document exchange, agent communication
- **WhatsApp** — driver coordination, customer updates
- **Shared network drives** — BOL PDFs, customs filing records
- **QuickBooks** — accounting, disconnected from operations
- **Manual CBP portal logins** — AES/ISF filings entered by hand

These operators are not choosing spreadsheets because they prefer them. They are choosing them because existing mid-market software has not crossed their cost-to-value threshold — too expensive to implement, too complex to learn, too bloated with features they don't need.

This represents a real and largely unserved market. The architectural goal of this workshop is not just to understand a legacy codebase — it is to chart a path from a 25-year-old freight forwarding monolith to a multi-tenant SaaS platform that this market can actually afford and use.

---

## About Eazy Freight Inc

Eazy Freight Inc is a freight forwarding company that arranges the international movement of cargo on behalf of shippers and consignees. The company acts as an intermediary between customers and carriers — purchasing capacity wholesale from ocean carriers and airlines, then reselling that capacity to customers, managing all documentation, regulatory filings, and logistics coordination in between.

Eazy Freight has been operating since the late 1990s. Its core software system has grown organically over that period, accumulating features across multiple operational domains as the business expanded from ocean freight into air freight, warehousing, and accounting. It is a textbook brownfield system: functionally comprehensive, architecturally undifferentiated.

---

## The SaaS Vision

The transformation goal is to evolve the Eazy Freight monolith into a multi-tenant SaaS platform targeting small independent freight forwarders currently running on Excel and email. This vision shapes every architectural decision:

- Each part of the system must support tenant isolation
- Pricing must match how small forwarders think — per shipment volume, not per user or per module
- Onboarding must be self-service — a 10-person shop cannot absorb a 3-month implementation project
- The UX must be accessible to non-technical operators
- The platform must be white-label capable — small forwarders present the software as their own to their customers

**The SaaS migration path follows four phases:**

**Phase 1 — Replace the spreadsheets**
Rate/Quote Management, Booking, Document Generation. These are the highest-pain Excel workflows. Getting a forwarder off their rate spreadsheet and booking email threads is the beachhead.

**Phase 2 — Replace manual compliance**
Export and Import compliance filings (AES, ISF, AMS), Denied Party Screening, Alerts. These are compliance risks that Excel cannot mitigate. The threat of CBP penalties is a strong motivator.

**Phase 3 — Replace disconnected accounting**
Customer Invoicing, Carrier Payables, Agent Settlement, Storage Fees. Connecting operations to accounting in one platform eliminates the QuickBooks disconnect.

**Phase 4 — Competitive differentiators**
Customer Portal, Real-time tracking via INTTRA, Analytics and Profit-per-file reporting. These are capabilities that Excel can never offer — they become retention drivers.

**Pricing model:**
- Free tier: up to 10 shipments/month — gets operators off Excel, creates stickiness
- Growth tier: up to 100 shipments/month
- Professional tier: unlimited shipments, full compliance filing, customer portal, analytics

---

## Business Modes

Eazy Freight operates across four shipping modes:

- **Ocean Export** — arranging outbound ocean shipments from the US to international destinations
- **Ocean Import** — receiving inbound ocean shipments from overseas agents into the US
- **Air Export** — arranging outbound air cargo shipments
- **Air Import** — receiving inbound air cargo shipments

---

## Core Business Operations

### Rate & Tariff Management

Before a booking exists, a customer requests a freight quote. The forwarder looks up applicable carrier rates, applies markup, and sends a formal quotation. This is the entry point to the entire shipment lifecycle and often where the sales relationship lives.

Carrier rates are negotiated contracts with validity periods — they expire and must be renegotiated. Each rate has a buy rate (what the forwarder pays the carrier) and a sell rate (what the customer is quoted). The margin is the spread between them.

Surcharges are additional charges on top of base freight rates, very common in ocean freight:
- BAF (Bunker Adjustment Factor) — fuel surcharge
- CAF (Currency Adjustment Factor)
- PSS (Peak Season Surcharge)
- Terminal Handling Charges (THC) at origin and destination
- Documentation fees

Surcharges fluctuate independently of base rates and must be tracked separately. Rate sources include carrier contracts, co-loader agreements, and spot rates. INTTRA can also be a source of ocean spot rates.

A formal quotation is sent to the customer showing origin/destination, mode, base rate, applicable surcharges, validity period, transit time, and carrier options. Quotes expire — typically within 7 to 30 days. An accepted quote transitions to a Booking.

### Booking

Every shipment begins with a booking. A customer calls or submits a request. Eazy Freight submits a booking request to a carrier or co-loader via INTTRA. A booking confirmation is received back, after which Eazy Freight issues its own booking confirmation to the customer.

Bookings have a meaningful lifecycle. They can be confirmed, amended, cancelled, or reinstated. Vessel overbooking by the carrier is a real operational scenario — when it happens, the booking may be rolled to the next available vessel sailing (reinstated) or cancelled entirely at the customer's request. A reinstated booking preserves its original identity and booking reference — it is not a new booking.

Booking status updates are pulled from INTTRA either on a recurring scheduled basis or as a one-time ad-hoc pull triggered by operations staff.

### Equipment & Container Management (Ocean)

Ocean freight uses physical containers. Eazy Freight arranges container pickup from a carrier yard by a driver. The container number is not known at booking time — it arrives later, sometimes via a container availability list sent by the carrier as an Excel spreadsheet via email, manually entered by operations staff.

Once the customer loads the container and returns it to the port, the door is sealed and a seal number is issued. If customs authorities open the container for inspection, a new seal number is issued.

### Trucking & Delivery

When transport needs to be arranged, a truck delivery order is generated and a driver is dispatched. Trucking is a supporting operation that applies across all four shipping modes — container pickup on ocean export, cargo delivery on ocean import, and pickup/delivery on air modes. The company may use its own fleet or arrange third-party trucking.

### Export Compliance & Customs Filing

**Ocean Export:**
Prior to vessel departure, Eazy Freight files Electronic Export Information (EEI) via the Automated Export System (AES) with US Customs and Border Protection (CBP). CBP returns an Internal Transaction Number (ITN#) confirming acceptance. This ITN# is required on the Master BOL instructions sent to the carrier. If cargo details change after filing, an EEI amendment must be submitted.

**Ocean Import:**
Two filings are required for inbound ocean shipments. The Automated Manifest System (AMS) filing is submitted before vessel departure from the origin port. The Importer Security Filing (ISF, 10+2) must be submitted at least 24 hours before cargo is loaded at origin. CBP may issue a hold on arrival, requiring terminal inspection before cargo is released.

**Air:**
Air cargo has its own regulatory filing requirements including ACE (Automated Commercial Environment) and ACAS (Air Cargo Advance Screening), a mandatory pre-departure security filing.

**Denied Party Screening:**
Before any shipment can proceed, the consignee and shipper must be screened against US Treasury and CBP denied party lists. This is a non-negotiable compliance requirement — a check that either clears or blocks a shipment from proceeding.

### Documentation

Eazy Freight generates a chain of documents at specific stages of the shipment lifecycle. Each document has a defined trigger, recipient, and dependency on prior information being available.

**Ocean documents generated during a shipment:**
- Booking Request PDF
- Booking Confirmation PDF — sent to customer
- Truck Delivery Order PDF — if transport arranged
- House BOL PDF — sent to shipper and consignee
- Master BOL with Carrier Instructions PDF — sent to carrier, requires ITN#
- Electronic transmission of booking and shipping instructions via INTTRA

**Air documents:**
- Air Waybill (AWBL) — uses IATA airport codes rather than port codes
- Manifest — prepared per flight, submitted to airport

A BOL may need to be reissued if cargo details, party information, or vessel details change. Reissuance preserves the original BOL reference with a revision number. The prior version is formally voided. If cargo details changed after ITN# was obtained, a CBP amendment is also required.

### Ocean Import Operations

Inbound ocean shipments originate with an overseas agent at the origin. The agent prepares documents, provides their House BOL, and issues debit and credit notes covering origin-side charges. When the shipment arrives at the US port, cargo is placed into holding. The customer is notified, presents their BOL, makes payment, and cargo is released. A release document is generated and signed off by the warehouse agent, formally transferring custody.

### Air Operations

Air export: the customer drops off the package, a manifest is prepared and sent to the airport, and the customer is billed immediately — no net payment terms apply. Air import generates an arrival notice when cargo lands, followed by a release document upon customer pickup.

### Warehousing

Eazy Freight operates warehouse facilities used for cargo consolidation (staging multiple customers' cargo for LCL ocean shipments), receiving, storage, and release. The warehouse agent plays a formal role in the cargo release process, providing sign-off that transfers custody. Warehouse operations also include inventory visibility and occupancy management.

### Finance & Accounting

**Ocean Export billing:**
Invoice generated after House BOL is issued. Payment terms: two weeks before the vessel arrives at destination, or Net 30. When customer payment is received, the carrier must be paid within two business days.

**Air Export billing:**
Customer billed immediately upon cargo drop-off. No net payment terms.

**Ocean Import billing:**
Customer payment required before cargo release.

**Agent settlement:**
Debit and credit notes from overseas agents are reconciled against inbound shipments, with multi-currency implications. Origin charges may be denominated in CNY, EUR, or other currencies.

**Storage fees:**
Accrue when a container is delivered to port before the vessel is ready (early delivery), or when the carrier does not receive BOL instructions in time. Assessed per diem after the carrier's free time allowance expires.

### Alerts & Operational Notifications

Time-sensitive alerts are a critical operational necessity. The business operates against hard external deadlines — vessel ETDs, CBP filing cutoffs, free time windows for containers.

**Export alerts:**
- ETD approaching — ITN# not yet filed
- ETD approaching — Master BOL instructions not yet sent
- Container not yet picked up — ETD imminent
- Customer payment not received within terms
- Carrier payment overdue
- Storage fee accruing

**Import alerts:**
- ETA approaching — customer not yet notified
- Cargo in holding — demurrage clock running
- Overseas agent documents not yet received
- ISF not yet filed — vessel departure imminent

### Customer & Party Management

Shippers, consignees, overseas agents, carriers, co-loaders, and trucking vendors are all distinct party types with different roles, contact information, and relationship terms.

### Customer Self-Service Portal

A web-accessible portal allowing customers to track shipment status, view documents, and receive notifications. A white-label surface — each tenant's customers see that tenant's branding.

### Reporting & Analytics

Operational dashboards and management reporting: profit-per-file, carrier performance, shipment volume by mode, outstanding receivables, demurrage exposure.

---

## SaaS Platform Operations

These capabilities do not exist in the current monolith. They are required for the SaaS transformation.

**Tenant Onboarding:**
A new freight forwarder signing up must be self-service onboarded — company setup, carrier credentials, INTTRA integration, AES filer ID registration, user accounts, and document template branding. Each forwarder operates under their own regulatory credentials.

**User Management & Access Control:**
Tenant-scoped users with role-based access control (admin, operations, accounting, read-only), with potential branch-level scoping for forwarders with multiple offices.

**Tenant Configuration:**
Each freight forwarder has their own operational policies — payment term defaults, document branding, surcharge structures, carrier preferences, and notification preferences.

**Subscription & Usage Metering:**
Subscription tier management, usage metering (shipments processed, BOLs generated, filings submitted), trial management, and subscription billing — distinct from freight billing.

---

## Key External Actors & Systems

| Actor / System | Relationship |
|---|---|
| Ocean Carriers | Eazy Freight purchases vessel capacity wholesale |
| Airlines | Eazy Freight purchases air cargo capacity |
| Co-loaders | Peer forwarders providing excess vessel capacity |
| Overseas Agents | Origin-side partners providing import documents and debit/credit notes |
| CBP / US Customs | Regulatory authority; Eazy Freight files on CBP's terms |
| INTTRA (e2open) | Industry carrier network; electronic booking and instruction exchange |
| Terminal / Port | Holds cargo; issues demurrage after free time expires |
| Denied Party Lists | US Treasury / CBP screening lists; mandatory pre-shipment check |

---

## Key Domain Concepts

| Concept | Description |
|---|---|
| Booking# | Primary identity anchor for a shipment |
| Container# | Not known at booking time; arrives later in the lifecycle |
| Seal# | Issued when container is loaded; replaced if customs opens the container |
| ITN# | Issued by CBP on EEI acceptance; required on Master BOL before carrier submission |
| Free Time | Grace period granted by carrier before storage/demurrage fees begin |
| Demurrage | Fee charged when container remains at port beyond free time |
| Reinstatement | Moving a cancelled booking to the next vessel sailing; original identity preserved |
| Master BOL | Document between forwarder and carrier; one per container |
| House BOL | Document between forwarder and customer; one per shipper/consignee pair |
| AWBL | Air Waybill; the air equivalent of a House BOL; uses IATA airport codes |
| LCL | Less than Container Load; multiple customers sharing one container |
| FCL | Full Container Load; one customer per container |
| TEU | Twenty-foot Equivalent Unit; standard container capacity measure |
| Co-loader | A peer forwarder with excess vessel capacity sold to other forwarders |
| NVOCC | Non-Vessel Operating Common Carrier; what Eazy Freight is licensed as |
| INTTRA | The world's largest ocean carrier network platform; now part of e2open |
| Buy Rate | The rate the forwarder pays the carrier |
| Sell Rate | The rate the forwarder charges the customer |
| Surcharge | Additional charge on top of base freight rate (BAF, CAF, PSS, THC) |
| AES / EEI | Automated Export System / Electronic Export Information; US export filing |
| AMS | Automated Manifest System; ocean cargo manifest filed with CBP pre-departure |
| ISF | Importer Security Filing (10+2); filed 24hrs before cargo loaded at origin |
| ACE | Automated Commercial Environment; US customs filing for air cargo |
| ACAS | Air Cargo Advance Screening; pre-departure air security filing |
| Tenant | A freight forwarding company using the SaaS platform |

---

## Workshop Scope

**In scope:** All four shipping modes (Ocean Export, Ocean Import, Air Export, Air Import), Warehousing, Accounting, Rate/Quote Management, and the SaaS transformation.

**Out of scope:** Customs clearance at the destination (consignee-side import compliance in the destination country).

---

*Eazy Freight Inc is a fictional company created for workshop purposes. The domain is based on real freight forwarding operations and real market conditions in the small freight forwarder segment.*
