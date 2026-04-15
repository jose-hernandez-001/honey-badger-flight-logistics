#!/bin/sh
# launcher.sh – Run flight-logistics-client from the VS Code snap terminal.
#
# VS Code (installed as a snap) injects GTK_PATH and GTK_EXE_PREFIX into every
# integrated terminal.  Those variables cause GTK to load
#   /snap/code/.../libcanberra-gtk-module.so
# which carries an RPATH pointing to /snap/core20/current/lib/...
# That path contains an Ubuntu-20.04-era libpthread incompatible with the
# system glibc on Ubuntu 25.10, producing:
#   symbol lookup error: .../libpthread.so.0: undefined symbol: __libc_pthread_init
#
# Unsetting the snap-injected variables lets the system dynamic linker use
# the correct /lib/x86_64-linux-gnu/libpthread.so.0 instead.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BINARY="$SCRIPT_DIR/build/flight-logistics-client"

if [ ! -x "$BINARY" ]; then
    echo "Binary not found: $BINARY" >&2
    echo "Build the project first:" >&2
    echo "  cd qt-client && cmake -B build -G Ninja && cmake --build build" >&2
    exit 1
fi

exec env \
    -u GTK_PATH \
    -u GTK_EXE_PREFIX \
    -u GTK_IM_MODULE_FILE \
    -u GIO_MODULE_DIR \
    -u GSETTINGS_SCHEMA_DIR \
    QT_QPA_PLATFORMTHEME= \
    "$BINARY" "$@"
