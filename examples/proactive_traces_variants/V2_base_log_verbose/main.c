#include <stdio.h>

static void log_message(const char *message) {
    fprintf(stderr, "[log] %s\n", message);
}

int main(void) {
    log_message("starting");
    printf("Hello!\n");
    return 0;
}
