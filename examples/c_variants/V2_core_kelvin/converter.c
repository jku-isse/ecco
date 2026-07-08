#include "converter.h"
#include <stdio.h>

float celsiusToFahrenheit(float celsius) {
    return celsius * 9.0 / 5.0 + 32.0;
}

float celsiusToKelvin(float celsius) {
    return celsius + 273.15;
}
