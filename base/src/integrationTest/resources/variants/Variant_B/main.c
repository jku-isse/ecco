#include <stdio.h>

int main() {
    printf("Base Product\n");

    // Feature B
    featureB();

    // Feature A || B
    featureAOrB();

    // Feature not A
    featureNA();

    return 0;
}

void featureB() {
    printf("Hello, this is Feature B!\n");
}

void featureAOrB() {
    printf("Hello, this is Feature A || B!\n");
}

void featureNA() {
    printf("Hello, this is Feature not A!\n");
}