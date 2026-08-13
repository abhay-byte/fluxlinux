#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/*
 * FluxLinux: glycin/GTK spawn "bwrap" with many --ro-bind paths.
 * Exec the glycin loader (or /usr/bin/true from the availability probe),
 * never an earlier bind source like /etc/ld.so.cache.
 */
int main(int argc, char **argv) {
    int i;
    int pick = -1;

    for (i = 1; i < argc; i++) {
        struct stat st;
        if (argv[i][0] != '/')
            continue;
        if (stat(argv[i], &st) != 0 || !S_ISREG(st.st_mode))
            continue;
        if (access(argv[i], X_OK) != 0)
            continue;
        /* Only the real loader under /usr — never ~/.cache/glycin/.../glycin-loaders */
        if ((strstr(argv[i], "/usr/libexec/glycin-loaders") != NULL ||
             strstr(argv[i], "/usr/lib/glycin-loaders") != NULL ||
             strcmp(argv[i], "/usr/bin/true") == 0 ||
             strcmp(argv[i], "/bin/true") == 0)) {
            pick = i;
            break;
        }
        pick = i; /* last regular executable as fallback */
    }

    if (pick < 0) {
        fprintf(stderr, "bwrap-shim: no command\n");
        return 127;
    }
    execv(argv[pick], &argv[pick]);
    perror("bwrap-shim execv");
    return 126;
}
