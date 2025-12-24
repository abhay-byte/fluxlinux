# Project Documentation Index

This folder contains the technical documentation for the Android Linux Container App. The huge reference document has been split into the following modular files for better readability:

*   **[Core Architecture & Design](architecture.md)**
    *   Analysis of the Tech Stack (Termux, Proot, X11).
    *   Critical Decision: "Embedded vs. Orchestrator" Architecture.

*   **[Component Analysis](components.md)**
    *   Deep dive into Containerization (Proot), Graphics (Termux:X11), and GPU Acceleration (Turnip/Zink).
    *   Reference links to external libraries.

*   **[Technical Specifications & Case Study](technical_specs.md)**
    *   **Case Study:** How "AnLinux" works (Clipboard method).
    *   **Our Specs:** How to use the Termux `RUN_COMMAND` Intent for superior automation.
    *   Code snippets for Intents and Permissions.

*   **[Roadmap & Implementation](roadmap.md)**
    *   Phased implementation plan (Foundation -> Bridge -> Distro Management).
    *   Advanced features strategy (Hardware Acceleration, Audio forwarding).

*   **[Problem Statement](problem_statement.md)**
    *   Original vision, capabilities, and top 15 use cases.
