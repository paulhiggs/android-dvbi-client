# DVB-I Client for Android

An Android starter client for DVB-I service discovery and broadband playback.

## Acknowledgements

Created with assistance from GitHub Copilot, an AI coding assistant.

## Current functionality

- Load a DVB-I ServiceList document from a user-provided HTTP(S) URL.
- Query a Service List Registry with country, language, delivery, and regulator-list filters; select v8 list URLs.
- Parse service names, providers, identifiers, service instances, and advertised HTTP(S) stream locations.
- Browse services using default logical channel numbers and play compatible DASH or HLS streams with Android Media3.
- Request schedule metadata from a service's ContentGuideSource and display programme times, titles, synopses, and minimum ages.
- Limit downloaded service-list XML to 5 MB and report network/XML errors in the app.
- Support HTTP as well as HTTPS endpoints; HTTP traffic is unencrypted.

## Build

Open the project in Android Studio with JDK 17, Gradle 8.9+, and Android SDK Platform 35 installed, then run the `app` configuration.

## DVB-I scope and conformance

This is an initial implementation, not a complete A177r8-conformant client. The registry URL is user-supplied, registry signature policies are not verified, and programme-guide requests currently cover a fixed time window. Regional LCN selection/range processing, full guide pagination/detail retrieval, linked applications, tuner/broadcast integration, and other optional or delivery-specific behavior remain to be implemented.

DVB lists A177r8 as a draft of TS 103 770 V1.3.1 (30 June 2026); the published baseline is TS 103 770 V1.2.1. A177 defines service discovery and programme metadata, while playback delivery profiles are also specified separately. Confirm the desired conformance baseline and device capabilities before treating the app as production-ready.

References:

- [DVB-I Service Discovery specification and A177r8 materials](https://dvb.org/?standard=service-discovery-and-programme-metadata-for-dvb-i)
- [DVB-I verification and validation resources](https://dvb.org/specifications/verification-validation/dvb-i/)
- [DVB-I reference client](https://github.com/DVBProject/DVB-I-Reference-Client)
