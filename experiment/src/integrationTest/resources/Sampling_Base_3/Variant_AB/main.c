#include <stdio.h>

int main() {
    printf("Base Product\n");

    // Feature A
    featureA();

    // Feature B
    featureB();

    // Feature A || B
    featureAOrB();

    // Feature A && B
    featureAAndB();

    return 0;
}

void featureA() {
    printf("Hello, this is Feature A!\n");
}

void featureB() {
    printf("Hello, this is Feature B!\n");
}

void featureAOrB() {
    printf("Hello, this is Feature A || B!\n");
}

void featureAAndB() {
    printf("Hello, this is Feature A && B!\n");
}