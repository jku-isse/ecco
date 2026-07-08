#include "converter.h"
#include <stdio.h>

float celsiusToFahrenheit(float celsius) {
    return celsius * 9.0 / 5.0 + 32.0;
}

void logConversion(float celsius) {
    printf("[LOG] converting %.1fC\n", celsius);
}
