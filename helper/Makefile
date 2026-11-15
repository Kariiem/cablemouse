CFLAGS ?= -std=gnu99 -O2 -Wall -Wextra
ASSETS = ../app/src/main/assets/helpers
VECTORS = ../testvectors/protocol.txt
# Everything a helper binary is built from besides its back end.
SOURCES = main.c common/protocol.c common/stream_check.c common/status.c
HEADERS = backend.h common/protocol.h common/stream_check.h common/status.h

.PHONY: test linux legacy ape assets clean
test: build/test_protocol build/test_stream
	./build/test_protocol $(VECTORS)
	./build/test_stream

build/test_protocol: tests/test_protocol.c common/protocol.c common/protocol.h
	@mkdir -p build
	$(CC) $(CFLAGS) -o $@ tests/test_protocol.c common/protocol.c

build/test_stream: tests/test_stream.c common/stream_check.c common/stream_check.h
	@mkdir -p build
	$(CC) $(CFLAGS) -o $@ tests/test_stream.c common/stream_check.c

linux: build/cablemouse-linux
build/cablemouse-linux: $(SOURCES) backends/linux.c $(HEADERS)
	@mkdir -p build
	$(CC) $(CFLAGS) -o $@ $(SOURCES) backends/linux.c -ldl

legacy: build/cablemouse-legacy.exe
build/cablemouse-legacy.exe: $(SOURCES) backends/win.c $(HEADERS)
	@mkdir -p build
	i686-w64-mingw32-gcc $(CFLAGS) -DLEGACY_WIN32 -o $@ $(SOURCES) backends/win.c -lwinmm -luser32 -static -s -Wl,--no-insert-timestamp

# Universal APE; backend selection via IsWindows()/IsXnu()/IsLinux() is still TODO (needs cosmocc spike).
ape:
	@command -v cosmocc >/dev/null || { echo "cosmocc not found on PATH"; exit 1; }
	@echo "TODO: APE dispatcher (PLAN.md Phase 5 step 18)"; exit 1

# Copy the helper binaries and README into the Android app's assets (bundled into the APK).
assets: legacy
	mkdir -p $(ASSETS)
	cp build/cablemouse-legacy.exe dist/README.txt $(ASSETS)/

clean:
	rm -rf build
