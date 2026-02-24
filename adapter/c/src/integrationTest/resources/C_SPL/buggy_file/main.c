#include <stdio.h>

int main({
    printf("Base Product\n");

    // Feature A
    featureA();

    // Feature A || B
    featureAOrB();

    return 0;
}

void featureA() {
    printf("Hello, this is Feature A!\n");
}

void featureAOrB() {
    printf("Hello, this is Feature A || B!\n");
}