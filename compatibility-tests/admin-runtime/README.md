# Installed Admin runtime HTTP consumer

This standalone Boot-parent project consumes the installed branch artifact.
The test requires a matching `compat.test.jvm` and `compat.expected.vm` (`1.8`, `17` or `25`).
It asserts the actual VM, a unique installed runtime jar, real loopback HTTP text/multimodal requests, Unicode, ignored future request fields and response wire names.
MDC/Marker calls also exercise the actual logging implementation on the native VM.
The server binds127.0.0.1 on a random port and its application context is closed after the test.
No model/cloud/database credentials or producer target directories are used.
